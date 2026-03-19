package com.vamp.haron.data.ftp

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult

class HaronFtplet : DefaultFtplet() {

    override fun onConnect(session: FtpSession): FtpletResult {
        val addr = session.clientAddress?.toString() ?: "?"
        EcosystemLogger.i(HaronConstants.TAG, "FTP: connect from $addr")
        return FtpletResult.DEFAULT
    }

    override fun onLogin(session: FtpSession, request: FtpRequest): FtpletResult {
        val user = session.user?.name ?: "unknown"
        val addr = session.clientAddress?.toString() ?: "?"
        EcosystemLogger.i(HaronConstants.TAG, "FTP: login user=$user from $addr")
        return FtpletResult.DEFAULT
    }

    override fun onUploadStart(session: FtpSession, request: FtpRequest): FtpletResult {
        val user = session.user?.name ?: "?"
        val file = request.argument ?: "?"
        EcosystemLogger.d(HaronConstants.TAG, "FTP: upload start user=$user file=$file")
        return FtpletResult.DEFAULT
    }

    override fun onUploadEnd(session: FtpSession, request: FtpRequest): FtpletResult {
        val user = session.user?.name ?: "?"
        val file = request.argument ?: "?"
        EcosystemLogger.i(HaronConstants.TAG, "FTP: upload complete user=$user file=$file")
        return FtpletResult.DEFAULT
    }

    override fun onDownloadStart(session: FtpSession, request: FtpRequest): FtpletResult {
        val user = session.user?.name ?: "?"
        val file = request.argument ?: "?"
        EcosystemLogger.d(HaronConstants.TAG, "FTP: download start user=$user file=$file")
        return FtpletResult.DEFAULT
    }

    override fun onDownloadEnd(session: FtpSession, request: FtpRequest): FtpletResult {
        val user = session.user?.name ?: "?"
        val file = request.argument ?: "?"
        EcosystemLogger.i(HaronConstants.TAG, "FTP: download complete user=$user file=$file")
        return FtpletResult.DEFAULT
    }

    override fun onDeleteStart(session: FtpSession, request: FtpRequest): FtpletResult {
        val user = session.user?.name ?: "?"
        val file = request.argument ?: "?"
        EcosystemLogger.d(HaronConstants.TAG, "FTP: delete user=$user file=$file")
        return FtpletResult.DEFAULT
    }

    override fun onMkdirStart(session: FtpSession, request: FtpRequest): FtpletResult {
        val user = session.user?.name ?: "?"
        val dir = request.argument ?: "?"
        EcosystemLogger.d(HaronConstants.TAG, "FTP: mkdir user=$user dir=$dir")
        return FtpletResult.DEFAULT
    }

    override fun onDisconnect(session: FtpSession): FtpletResult {
        val user = session.user?.name ?: "?"
        EcosystemLogger.i(HaronConstants.TAG, "FTP: disconnect user=$user")
        return FtpletResult.DEFAULT
    }
}
