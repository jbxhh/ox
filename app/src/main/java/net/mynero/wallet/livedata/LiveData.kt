package net.mynero.wallet.livedata

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

fun <T1, T2, S> combineLatest(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    func: (T1?, T2?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        result.value = func.invoke(source1.value, source2.value)
    }
    result.addSource(source2) {
        result.value = func.invoke(source1.value, source2.value)
    }
    return result
}

fun <T1, T2, T3, S> combineLatest(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    source3: LiveData<T3>,
    func: (T1?, T2?, T3?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        result.value = func.invoke(source1.value, source2.value, source3.value)
    }
    result.addSource(source2) {
        result.value = func.invoke(source1.value, source2.value, source3.value)
    }
    result.addSource(source3) {
        result.value = func.invoke(source1.value, source2.value, source3.value)
    }
    return result
}

fun <T1, T2, T3, T4, S> combineLatest(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    source3: LiveData<T3>,
    source4: LiveData<T4>,
    func: (T1?, T2?, T3?, T4?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        result.value = func.invoke(source1.value, source2.value, source3.value, source4.value)
    }
    result.addSource(source2) {
        result.value = func.invoke(source1.value, source2.value, source3.value, source4.value)
    }
    result.addSource(source3) {
        result.value = func.invoke(source1.value, source2.value, source3.value, source4.value)
    }
    result.addSource(source4) {
        result.value = func.invoke(source1.value, source2.value, source3.value, source4.value)
    }

    return result
}

fun <T1, T2, T3, T4, T5, S> combineLatest(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    source3: LiveData<T3>,
    source4: LiveData<T4>,
    source5: LiveData<T5>,
    func: (T1?, T2?, T3?, T4?, T5?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        result.value = func.invoke(source1.value, source2.value, source3.value, source4.value, source5.value)
    }
    result.addSource(source2) {
        result.value = func.invoke(source1.value, source2.value, source3.value, source4.value, source5.value)
    }
    result.addSource(source3) {
        result.value = func.invoke(source1.value, source2.value, source3.value, source4.value, source5.value)
    }
    result.addSource(source4) {
        result.value = func.invoke(source1.value, source2.value, source3.value, source4.value, source5.value)
    }
    result.addSource(source5) {
        result.value = func.invoke(source1.value, source2.value, source3.value, source4.value, source5.value)
    }

    return result
}

fun <T1, T2, S> combineLatestIgnoreNull(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    func: (T1?, T2?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        func.invoke(source1.value, source2.value)?.run { result.value = this }
    }
    result.addSource(source2) {
        func.invoke(source1.value, source2.value)?.run { result.value = this }
    }
    return result
}

fun <T1, T2, T3, S> combineLatestIgnoreNull(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    source3: LiveData<T3>,
    func: (T1?, T2?, T3?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        func(source1.value, source2.value, source3.value)?.run { result.value = this }
    }

    result.addSource(source2) {
        func(source1.value, source2.value, source3.value)?.run { result.value = this }
    }

    result.addSource(source3) {
        func(source1.value, source2.value, source3.value)?.run { result.value = this }
    }

    return result
}

fun <T1, T2, T3, T4, S> combineLatestIgnoreNull(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    source3: LiveData<T3>,
    source4: LiveData<T4>,
    func: (T1?, T2?, T3?, T4?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        func(source1.value, source2.value, source3.value, source4.value)?.run { result.value = this }
    }

    result.addSource(source2) {
        func(source1.value, source2.value, source3.value, source4.value)?.run { result.value = this }
    }

    result.addSource(source3) {
        func(source1.value, source2.value, source3.value, source4.value)?.run { result.value = this }
    }

    result.addSource(source4) {
        func(source1.value, source2.value, source3.value, source4.value)?.run { result.value = this }
    }

    return result
}

fun <T1, T2, T3, T4, T5, S> combineLatestIgnoreNull(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    source3: LiveData<T3>,
    source4: LiveData<T4>,
    source5: LiveData<T5>,
    func: (T1?, T2?, T3?, T4?, T5?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value
        )?.run { result.value = this }
    }

    result.addSource(source2) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value
        )?.run { result.value = this }
    }

    result.addSource(source3) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value
        )?.run { result.value = this }
    }

    result.addSource(source4) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value
        )?.run { result.value = this }
    }

    result.addSource(source5) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value
        )?.run { result.value = this }
    }

    return result
}

fun <T1, T2, T3, T4, T5, T6, S> combineLatestIgnoreNull(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    source3: LiveData<T3>,
    source4: LiveData<T4>,
    source5: LiveData<T5>,
    source6: LiveData<T6>,
    func: (T1?, T2?, T3?, T4?, T5?, T6?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value
        )?.run { result.value = this }
    }

    result.addSource(source2) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value
        )?.run { result.value = this }
    }

    result.addSource(source3) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value
        )?.run { result.value = this }
    }

    result.addSource(source4) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value
        )?.run { result.value = this }
    }

    result.addSource(source5) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value
        )?.run { result.value = this }
    }

    result.addSource(source6) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value
        )?.run { result.value = this }
    }

    return result
}

fun <T1, T2, T3, T4, T5, T6, T7, T8, S> combineLatestIgnoreNull(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    source3: LiveData<T3>,
    source4: LiveData<T4>,
    source5: LiveData<T5>,
    source6: LiveData<T6>,
    source7: LiveData<T7>,
    source8: LiveData<T8>,
    func: (T1?, T2?, T3?, T4?, T5?, T6?, T7?, T8?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value,
            source7.value,
            source8.value
        )?.run { result.value = this }
    }

    result.addSource(source2) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value,
            source7.value,
            source8.value
        )?.run { result.value = this }
    }

    result.addSource(source3) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value,
            source7.value,
            source8.value
        )?.run { result.value = this }
    }

    result.addSource(source4) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value,
            source7.value,
            source8.value
        )?.run { result.value = this }
    }

    result.addSource(source5) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value,
            source7.value,
            source8.value
        )?.run { result.value = this }
    }

    result.addSource(source6) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value,
            source7.value,
            source8.value
        )?.run { result.value = this }
    }

    result.addSource(source7) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value,
            source7.value,
            source8.value
        )?.run { result.value = this }
    }

    result.addSource(source8) {
        func(
            source1.value,
            source2.value,
            source3.value,
            source4.value,
            source5.value,
            source6.value,
            source7.value,
            source8.value
        )?.run { result.value = this }
    }

    return result
}