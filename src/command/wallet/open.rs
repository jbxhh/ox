use std::{io::Write, path::PathBuf, sync::Arc};
use tokio::io::{AsyncBufReadExt, BufReader};

use monfluo_oxide::wallet::Wallet;

use crate::command::wallet::session::{Session, parse};

pub async fn run(monfluo_directory: PathBuf, name: String, password: String) {
    match Wallet::open(monfluo_directory, &name, &password).await {
        Ok(wallet) => {
            println!("Wallet opened");
            run_interactive(Arc::new(wallet)).await;
        }
        Err(e) => println!("Failed to open wallet: {e}"),
    }
}

async fn run_interactive(wallet: Arc<Wallet>) {
    let mut session = Session::new(wallet);
    let mut stdin = BufReader::new(tokio::io::stdin());
    loop {
        let Some(input) = read_input(&session.wallet, &mut stdin).await else {
            break;
        };
        if input.is_empty() {
            continue;
        }
        match parse(&input) {
            Ok(command) => {
                if !session.execute(command).await {
                    break;
                }
            }
            Err(e) => print!("{}", e.render()),
        }
    }

    if let Err(e) = session.wallet.store().await {
        println!("Failed to store wallet: {e}");
    }
}

// returns None on EOF, so that piping commands into the CLI terminates the session
async fn read_input<R: AsyncBufReadExt + Unpin>(wallet: &Wallet, stdin: &mut R) -> Option<String> {
    print!("[Monero Wallet '{}'] > ", wallet.name().unwrap_or("???"));
    std::io::stdout().flush().ok()?;
    let mut input = String::new();
    match stdin.read_line(&mut input).await {
        Ok(0) | Err(_) => None,
        Ok(_) => Some(input.trim().to_owned()),
    }
}
