/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.statistics

import org.json.JSONException
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Charts.DiffUtilsCallback
import org.telegram.ui.Charts.data.ChartData
import org.telegram.ui.Charts.data.StackLinearChartData
import org.telegram.ui.Components.RecyclerListView

class ChartViewData(val title: String, val graphType: Int) {
	var isError = false
	var errorMessage: String? = null
	var activeZoom: Long = 0
	var viewShowed = false
	var chartData: ChartData? = null
	var childChartData: ChartData? = null
	var token: String? = null
	var zoomToken: String? = null
	var loading = false
	var isEmpty = false
	var isLanguages = false
	var useHourFormat = false
	var useWeekFormat = false

	fun load(accountId: Int, classGuid: Int, recyclerListView: RecyclerListView?, difCallback: DiffUtilsCallback?) {
		if (!loading) {
			loading = true

			val request = TLRPC.TL_stats_loadAsyncGraph()
			request.token = token

			val reqId = ConnectionsManager.getInstance(accountId).sendRequest(request, { response, error ->
				var chartData: ChartData? = null
				var zoomToken: String? = null

				if (error == null) {
					if (response is TLRPC.TL_statsGraph) {
						val json = response.json.data

						if (!json.isNullOrEmpty()) {
							try {
								chartData = StatisticActivity.createChartData(JSONObject(json), graphType, isLanguages)
								zoomToken = response.zoom_token

								if (graphType == 4 && chartData?.x != null && chartData.x.isNotEmpty()) {
									val x = chartData.x[chartData.x.size - 1]
									childChartData = StackLinearChartData(chartData, x)
									activeZoom = x
								}
							}
							catch (e: JSONException) {
								FileLog.e(e)
							}
						}
					}

					if (response is TLRPC.TL_statsGraphError) {
						isEmpty = false
						isError = true
						errorMessage = response.error
					}
				}

				val finalChartData = chartData
				val finalZoomToken = zoomToken

				AndroidUtilities.runOnUIThread {
					loading = false

					this.chartData = finalChartData
					this.zoomToken = finalZoomToken

					val n = recyclerListView!!.childCount
					var found = false

					for (i in 0 until n) {
						val child = recyclerListView.getChildAt(i)

						if (child is StatisticActivity.ChartCell && child.data === this) {
							child.updateData(this, true)
							found = true
							break
						}
					}

					if (!found) {
						recyclerListView.itemAnimator = null
						difCallback!!.update()
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)

			ConnectionsManager.getInstance(accountId).bindRequestToGuid(reqId, classGuid)
		}
	}
}
