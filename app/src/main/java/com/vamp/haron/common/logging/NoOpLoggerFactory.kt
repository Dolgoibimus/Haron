package com.vamp.haron.common.logging

import org.slf4j.ILoggerFactory
import org.slf4j.Logger

/**
 * Фабрика, возвращающая единственный NoOpLogger для всех запросов.
 */
class NoOpLoggerFactory : ILoggerFactory {
    override fun getLogger(name: String?): Logger = NoOpLogger
}
