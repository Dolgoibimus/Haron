package com.vamp.haron.data.cast

import android.content.Context
import android.media.MediaRouter
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.CastDevice
import com.vamp.haron.domain.model.CastType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MiracastManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRouter: MediaRouter? = null

    fun discoverDisplays(): Flow<List<CastDevice>> = callbackFlow {
        val router = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
        mediaRouter = router

        if (router == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, info: MediaRouter.RouteInfo) {
                trySend(getAvailableRoutes(router))
            }

            override fun onRouteRemoved(router: MediaRouter, info: MediaRouter.RouteInfo) {
                trySend(getAvailableRoutes(router))
            }

            override fun onRouteChanged(router: MediaRouter, info: MediaRouter.RouteInfo) {
                trySend(getAvailableRoutes(router))
            }

            override fun onRouteSelected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) {}
            override fun onRouteUnselected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) {}
            override fun onRouteGrouped(router: MediaRouter, info: MediaRouter.RouteInfo, group: MediaRouter.RouteGroup, index: Int) {}
            override fun onRouteUngrouped(router: MediaRouter, info: MediaRouter.RouteInfo, group: MediaRouter.RouteGroup) {}
            override fun onRouteVolumeChanged(router: MediaRouter, info: MediaRouter.RouteInfo) {}
        }

        router.addCallback(
            MediaRouter.ROUTE_TYPE_LIVE_VIDEO,
            callback,
            MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
        )

        // Initial scan
        trySend(getAvailableRoutes(router))

        awaitClose {
            router.removeCallback(callback)
        }
    }

    fun selectRoute(routeId: String) {
        val router = mediaRouter ?: return
        val routeCount = router.routeCount
        for (i in 0 until routeCount) {
            val route = router.getRouteAt(i)
            if (route.name?.toString() == routeId || route.tag == routeId) {
                router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, route)
                EcosystemLogger.d(HaronConstants.TAG, "Miracast route selected: ${route.name}")
                return
            }
        }
    }

    private fun getAvailableRoutes(router: MediaRouter): List<CastDevice> {
        val devices = mutableListOf<CastDevice>()
        val routeCount = router.routeCount
        for (i in 0 until routeCount) {
            val route = router.getRouteAt(i)
            // Filter to external display routes only
            if (route.supportedTypes and MediaRouter.ROUTE_TYPE_LIVE_VIDEO != 0 &&
                route != router.defaultRoute
            ) {
                devices.add(
                    CastDevice(
                        id = route.name?.toString() ?: "route_$i",
                        name = route.name?.toString() ?: "Display $i",
                        type = CastType.MIRACAST
                    )
                )
            }
        }
        return devices
    }
}
