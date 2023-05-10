package com.ojhdtapp.paraboxdevelopmentkit.model.res_info

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface ParaboxResourceInfo : Parcelable {
    @Parcelize
    object ParaboxEmptyInfo: ParaboxResourceInfo
    sealed interface ParaboxLocalInfo : ParaboxResourceInfo {
        suspend fun upload(service: ParaboxCloudService): ParaboxSyncedInfo {
            return ParaboxSyncedInfo(
                local = this,
                remote = service.upload()
            )
        }

        @Parcelize
        data class UriLocalInfo(val uri: Uri) : ParaboxLocalInfo
    }

    sealed interface ParaboxRemoteInfo : ParaboxResourceInfo {
        suspend fun download(service: ParaboxCloudService): ParaboxSyncedInfo {
            return ParaboxSyncedInfo(
                local = service.download(),
                remote = this
            )
        }
        @Parcelize
        data class UrlRemoteInfo(val url: String) : ParaboxRemoteInfo
        @Parcelize
        data class DriveRemoteInfo(val uuid: String, val cloudPath: String) : ParaboxRemoteInfo
    }

    @Parcelize
    class ParaboxSyncedInfo(val local: ParaboxLocalInfo, val remote: ParaboxRemoteInfo) :
        ParaboxLocalInfo, ParaboxRemoteInfo
}