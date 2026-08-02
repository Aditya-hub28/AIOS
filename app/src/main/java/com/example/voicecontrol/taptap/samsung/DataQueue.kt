package com.example.voicecontrol.taptap.samsung

import java.util.ArrayDeque

class DataQueue<T>(private val capacity: Int) {
    private val queue = ArrayDeque<T>()

    fun add(element: T) {
        if (queue.size >= capacity) {
            queue.pollFirst()
        }
        queue.addLast(element)
    }

    fun clear() {
        queue.clear()
    }

    fun toList(): List<T> = queue.toList()
    val size: Int get() = queue.size
}
