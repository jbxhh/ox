use std::{path::PathBuf, sync::Arc};

use monfluo_oxide::wallet::Wallet;

use crate::command::wallet::session::{Session, parse};

pub async fn run(monfluo_directory: PathBuf, name: String, password: String, commands: Vec<String>) {
    let wallet = match Wallet::open(monfluo_directory, &name, &password).await {
        Ok(wallet) => wallet,
        Err(e) => {
            println!("Failed to open wallet: {e}");
            return;
        }
    };
    // parse everything before executing anything, so that a typo in the last command doesn't leave us half-executed
    let commands = match commands
        .iter()
        .map(|command| parse(command))
        .collect::<Result<Vec<_>, _>>()
    {
        Ok(commands) => commands,
        Err(e) => {
            print!("{}", e.render());
            return;
        }
    };
    let mut session = Session::new(Arc::new(wallet));
    for command in commands {
        if !session.execute(command).await {
            break;
        }
    }
    if let Err(e) = session.wallet.store().await {
        println!("Failed to store wallet: {e}");
    }
}
