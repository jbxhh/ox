// this file is a copy of monero-oxide's simple-request-transport, but uses reqwest instead of hyper

use core::{future::Future, time::Duration};
use std::sync::Arc;

use digest_auth::{AuthContext, WwwAuthenticateHeader};
use reqwest::{Client, StatusCode};
use tokio::sync::Mutex;
use url::Url;
use zeroize::Zeroizing;

use monero_daemon_rpc::{HttpTransport, MoneroDaemon, prelude::InterfaceError};

use crate::util::HexFormatted;

const DEFAULT_NODE_SCHEME: &str = "http";
const DEFAULT_PROXY_SCHEME: &str = "socks5h";

#[derive(Clone, Debug)]
enum Authentication {
    Unauthenticated,
    Authenticated { username: Zeroizing<String>, password: Zeroizing<String>, state: Arc<Mutex<Option<(WwwAuthenticateHeader, u64)>>> },
}

/// An HTTP(S) transport to connect to a Monero daemon.
///
/// Supports HTTP(S) daemons through an HTTP(S) or SOCKS proxy, with automatic digest authentication.
#[derive(Clone, Debug)]
pub struct MonfluoTransport {
    read_response_before_next_request: Arc<Mutex<()>>,
    authentication: Authentication,
    request_timeout: Duration,
    base_url: Url,
    client: Client,
}

impl MonfluoTransport {
    fn digest_auth_challenge(response: &reqwest::Response) -> Result<Option<(WwwAuthenticateHeader, u64)>, InterfaceError> {
        Ok(if let Some(header) = response.headers().get("www-authenticate") {
            Some((
                digest_auth::parse(
                    header
                        .to_str()
                        .map_err(|_| InterfaceError::InvalidInterface("www-authenticate header wasn't a string".to_owned()))?,
                )
                .map_err(|_| InterfaceError::InvalidInterface("invalid digest-auth response".to_owned()))?,
                0,
            ))
        } else {
            None
        })
    }

    // A bare `host:port` parses as a scheme, so only an authority means one was supplied
    fn parse_address(address: &str, default_scheme: &str) -> Result<Url, InterfaceError> {
        let address = address.trim();

        match Url::parse(address) {
            Ok(url) if url.host_str().is_some() => Ok(url),
            _ => Url::parse(&format!("{default_scheme}://{address}")).map_err(|e| InterfaceError::InterfaceError(format!("invalid address: {e}"))),
        }
    }

    fn parse_url(url: &Url) -> Result<(Url, Option<(String, String)>), InterfaceError> {
        let mut parsed = url.clone();

        let username = parsed.username();
        let password = parsed.password();

        if !username.is_empty() || password.is_some() {
            let username = username.to_string();
            let password = password.unwrap_or("").to_string();
            parsed
                .set_username("")
                .map_err(|_| InterfaceError::InterfaceError("failed to clear username from URL".to_owned()))?;
            parsed
                .set_password(None)
                .map_err(|_| InterfaceError::InterfaceError("failed to clear password from URL".to_owned()))?;
            Ok((parsed, Some((username, password))))
        } else {
            Ok((parsed, None))
        }
    }

    fn create_client(proxy: Option<reqwest::Proxy>) -> Result<Client, InterfaceError> {
        let mut builder = Client::builder();
        if let Some(p) = proxy {
            builder = builder.proxy(p);
        }
        builder
            .build()
            .map_err(|e| InterfaceError::InterfaceError(format!("failed to create HTTP client: {e}")))
    }

    /// Create a new HTTP(S) RPC connection.
    ///
    /// The URL is parsed with the [`url`] crate. A daemon requiring authentication may be used
    /// by including the username and password in the URL
    /// (`scheme://username:password@domain.tld`) or by passing them as `credentials`, which take
    /// precedence over the ones in the URL.
    ///
    /// # Arguments
    ///
    /// * `url` - The daemon URL (e.g., `http://localhost:18081` or `localhost:18081`)
    /// * `credentials` - Optional username and password
    /// * `proxy` - Optional proxy address, `socks5h` when the scheme is omitted (`127.0.0.1:9050`)
    /// * `request_timeout` - Maximum duration for each request
    pub async fn new(url: String, credentials: Option<(String, String)>, proxy: Option<String>, request_timeout: Duration) -> Result<MoneroDaemon<MonfluoTransport>, InterfaceError> {
        let url = Self::parse_address(&url, DEFAULT_NODE_SCHEME)?;
        let proxy = proxy
            .map(|proxy| Self::parse_address(&proxy, DEFAULT_PROXY_SCHEME))
            .transpose()?
            .map(reqwest::Proxy::all)
            .transpose()
            .map_err(|e| InterfaceError::InterfaceError(format!("invalid proxy: {e}")))?;
        let client = Self::create_client(proxy)?;
        let (base_url, url_credentials) = Self::parse_url(&url)?;
        let credentials = credentials.or(url_credentials);

        let authentication = if let Some((username, password)) = credentials {
            let response = tokio::time::timeout(request_timeout, client.post(base_url.clone()).body(vec![]).send())
                .await
                .map_err(|e| InterfaceError::InterfaceError(format!("timeout reached: {e:?}")))?
                .map_err(|e| InterfaceError::InterfaceError(format!("couldn't make request: {e:?}")))?;

            let challenge = Self::digest_auth_challenge(&response)?;

            Authentication::Authenticated {
                username: Zeroizing::new(username),
                password: Zeroizing::new(password),
                state: Arc::new(Mutex::new(challenge)),
            }
        } else {
            Authentication::Unauthenticated
        };

        MoneroDaemon::new(MonfluoTransport {
            read_response_before_next_request: Arc::new(Mutex::new(())),
            authentication,
            request_timeout,
            base_url,
            client,
        })
        .await
    }

    async fn inner_post(&self, route: &str, body: Vec<u8>, response_size_limit: Option<usize>) -> Result<Vec<u8>, InterfaceError> {
        let _guard = self.read_response_before_next_request.lock().await;

        let url = self
            .base_url
            .join(route)
            .map_err(|e| InterfaceError::InterfaceError(format!("invalid route: {e}")))?;

        for attempt in 0..2 {
            match &self.authentication {
                Authentication::Unauthenticated => {
                    let response = self
                        .client
                        .post(url.clone())
                        .body(body)
                        .send()
                        .await
                        .map_err(|e| InterfaceError::InterfaceError(format!("{e:?}")))?;
                    return Self::body_from_response(response, response_size_limit).await;
                }
                Authentication::Authenticated {
                    username,
                    password,
                    state,
                } => {
                    let mut state_lock = state.lock().await;

                    if state_lock.is_none() {
                        let response = self
                            .client
                            .post(url.clone())
                            .body(body.clone())
                            .send()
                            .await
                            .map_err(|e| InterfaceError::InterfaceError(format!("{e:?}")))?;
                        *state_lock = Self::digest_auth_challenge(&response)?;
                    }

                    let mut request_builder = self.client.post(url.clone()).body(body.clone());

                    if let Some((challenge, cnonce)) = state_lock.as_mut() {
                        *cnonce += 1;

                        let mut ctx = AuthContext::new_post::<_, _, _, &[u8]>(username.as_str(), password.as_str(), url.path(), None);
                        ctx.set_custom_cnonce(HexFormatted::borrowed(&cnonce.to_le_bytes()).to_string());

                        let auth_header = challenge
                            .respond(&ctx)
                            .map_err(|_| InterfaceError::InvalidInterface("couldn't respond to digest-auth challenge".to_owned()))?
                            .to_header_string();

                        request_builder = request_builder.header("Authorization", auth_header);
                    }

                    let response_result = request_builder.send().await;

                    let (response, error) = match response_result {
                        Ok(r) => (Some(r), None),
                        Err(e) => (None, Some(InterfaceError::InterfaceError(format!("{e:?}")))),
                    };

                    let (is_error, is_stale) = match &response {
                        Some(r) => (
                            false,
                            if r.status() == StatusCode::UNAUTHORIZED {
                                r.headers()
                                    .get("www-authenticate")
                                    .and_then(|h| h.to_str().ok())
                                    .map(|s| s.contains("stale"))
                                    .unwrap_or(false)
                            } else {
                                false
                            },
                        ),
                        None => (true, false),
                    };

                    if is_error || is_stale {
                        *state_lock = None;
                        if attempt == 0 {
                            continue;
                        }
                        if let Some(e) = error {
                            return Err(e);
                        } else {
                            debug_assert!(is_stale);
                            return Err(InterfaceError::InvalidInterface("node claimed fresh connection had stale authentication".to_owned()));
                        }
                    }

                    let response = response.expect("no response yet also no error?");
                    return Self::body_from_response(response, response_size_limit).await;
                }
            }
        }

        unreachable!()
    }

    async fn body_from_response(mut response: reqwest::Response, response_size_limit: Option<usize>) -> Result<Vec<u8>, InterfaceError> {
        let mut body = Vec::new();

        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|e| InterfaceError::InterfaceError(format!("{e:?}")))?
        {
            body.extend_from_slice(&chunk);

            if let Some(limit) = response_size_limit
                && body.len() > limit
            {
                return Err(InterfaceError::InterfaceError(format!("response size exceeds limit {}", limit)));
            }
        }

        Ok(body)
    }
}

impl HttpTransport for MonfluoTransport {
    fn post(&self, route: &str, body: Vec<u8>, response_size_limit: Option<usize>) -> impl Send + Future<Output = Result<Vec<u8>, InterfaceError>> {
        async move {
            tokio::time::timeout(self.request_timeout, self.inner_post(route, body, response_size_limit))
                .await
                .map_err(|e| InterfaceError::InterfaceError(format!("timeout reached: {e:?}")))?
        }
    }
}
