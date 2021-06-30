package com.hushsbay.sendjay.common

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class RxToUp {

    companion object {

        val publisher: PublishSubject<Any> = PublishSubject.create()

        inline fun <reified T> subscribe(): Observable<T> {
            return publisher.filter {
                it is T
            }.map {
                it as T
            }
        }

        fun post(event: Any) {
            publisher.onNext(event)
        }

    }

}