package com.hushsbay.sendjay.common

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class RxToDown { //https://selfish-developer.com/entry/RxJava-Subject-PublishSubject-BehaviorSubject, https://tourspace.tistory.com/281

    companion object {

        val publisher: PublishSubject<Any> = PublishSubject.create()

        inline fun <reified T> subscribe(): Observable<T> { //https://codechacha.com/ko/kotlin-reified-keyword
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