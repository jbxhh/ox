set positional-arguments

default:
    @just --list --justfile {{ justfile() }} --unsorted

run *ARGS:
    @MONFLUO_LOG=${MONFLUO_LOG:-info} cargo run --release -- "$@"

build:
    cargo build --release

test:
    cargo test

format:
    cargo fmt

lint:
    cargo clippy --all-targets

clean:
    cargo clean
