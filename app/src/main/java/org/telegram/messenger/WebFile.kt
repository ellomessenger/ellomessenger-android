/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.messenger

import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.DocumentAttribute
import org.telegram.tgnet.TLRPC.InputGeoPoint
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.TLRPC.InputWebFileLocation
import org.telegram.tgnet.TLRPC.TLInputGeoPoint
import org.telegram.tgnet.TLRPC.TLInputWebFileGeoPointLocation
import org.telegram.tgnet.TLRPC.TLInputWebFileLocation
import org.telegram.tgnet.TLRPC.TLWebDocument
import org.telegram.tgnet.TLRPC.WebDocument
import java.util.Locale

class WebFile : TLObject() {
	var geoPoint: InputGeoPoint? = null
	var peer: InputPeer? = null
	var msgId: Int = 0
	var w: Int = 0
	var h: Int = 0
	var zoom: Int = 0
	var scale: Int = 0
	var url: String? = null
	var location: InputWebFileLocation? = null
	var attributes: List<DocumentAttribute>? = null
	var size: Int = 0
	var mimeType: String? = null

	companion object {
		@JvmStatic
		fun createWithGeoPoint(point: TLRPC.GeoPoint?, w: Int, h: Int, zoom: Int, scale: Int): WebFile? {
			if (point is TLRPC.TLGeoPoint) {
				return createWithGeoPoint(point.lat, point.lon, point.accessHash, w, h, zoom, scale)
			}

			return null
		}

		fun createWithGeoPoint(latitude: Double, longitude: Double, accessHash: Long, w: Int, h: Int, zoom: Int, scale: Int): WebFile {
			val webFile = WebFile()

			val location = TLInputWebFileGeoPointLocation()

			webFile.location = location

			webFile.geoPoint = TLInputGeoPoint().also {
				it.lat = latitude
				it.lon = longitude
			}

			location.geoPoint = webFile.geoPoint
			location.accessHash = accessHash

			webFile.w = w

			location.w = webFile.w

			webFile.h = h

			location.h = webFile.h

			webFile.zoom = zoom

			location.zoom = webFile.zoom

			webFile.scale = scale

			location.scale = webFile.scale

			webFile.mimeType = "image/png"
			webFile.url = String.format(Locale.US, "maps_%.6f_%.6f_%d_%d_%d_%d.png", latitude, longitude, w, h, zoom, scale)
			webFile.attributes = listOf()

			return webFile
		}

		@JvmStatic
		fun createWithWebDocument(webDocument: WebDocument?): WebFile? {
			if (webDocument !is TLWebDocument) {
				return null
			}

			val webFile = WebFile()
			val location = TLInputWebFileLocation()

			webFile.location = location
			webFile.url = webDocument.url

			location.url = webFile.url
			location.accessHash = webDocument.accessHash

			webFile.size = webDocument.size
			webFile.mimeType = webDocument.mimeType
			webFile.attributes = webDocument.attributes

			return webFile
		}
	}
}
