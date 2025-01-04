package org.monfluo.wallet.livedata

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

fun <T1, T2, S> combineLiveDatas(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    func: (T1?, T2?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        func(
            source1.value,
            source2.value,
        )?.run { result.value = this }
    }

    result.addSource(source2) {
        func(
            source1.value,
            source2.value,
        )?.run { result.value = this }
    }

    return result
}

fun <T1, T2, T3, S> combineLiveDatas(
    source1: LiveData<T1>,
    source2: LiveData<T2>,
    source3: LiveData<T3>,
    func: (T1?, T2?, T3?) -> S?
): LiveData<S> {
    val result = MediatorLiveData<S>()
    result.addSource(source1) {
        func(
            source1.value,
            source2.value,
            source3.value,
        )?.run { result.value = this }
    }

    result.addSource(source2) {
        func(
            source1.value,
            source2.value,
            source3.value,
        )?.run { result.value = this }
    }

    result.addSource(source3) {
        func(
            source1.value,
            source2.value,
            source3.value,
        )?.run { result.value = this }
    }

    return result
}

fun <T1, T2, T3, T4, T5, T6, S> combineLiveDatas(
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
            source6.value,
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
        )?.run { result.value = this }
    }

    return result
}