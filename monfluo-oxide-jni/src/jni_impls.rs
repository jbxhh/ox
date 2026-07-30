use jni::{
    sys::{JNI_VERSION_1_6, jint},
    vm::JavaVM,
};
use tracing_subscriber::prelude::*;

pub mod daemon;
pub mod monfluo;
pub mod seed;
pub mod wallet;

#[unsafe(no_mangle)]
pub extern "system" fn JNI_OnLoad(
    sys_jvm: *mut jni::sys::JavaVM,
    _reserved: *mut std::ffi::c_void,
) -> jint {
    init_tracing();
    let jvm = unsafe { JavaVM::from_raw(sys_jvm) };
    jvm.with_top_local_frame(|mut env| {
        crate::java::init_cache(&mut env);
        Ok::<(), jni::errors::Error>(())
    })
    .expect("Failed to init java cache");
    JNI_VERSION_1_6
}

fn init_tracing() {
    let registry = tracing_subscriber::registry().with(tracing_subscriber::fmt::layer());

    #[cfg(target_os = "android")]
    {
        registry
            .with(tracing_android::layer("MONFLUO_OXIDE_JNI").unwrap())
            .init();
    }

    #[cfg(not(target_os = "android"))]
    {
        registry.init();
    }
}
