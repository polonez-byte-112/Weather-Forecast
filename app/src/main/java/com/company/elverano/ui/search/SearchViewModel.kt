package com.company.elverano.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.elverano.data.error.CustomError
import com.company.elverano.data.error.ErrorRepository
import com.company.elverano.data.historyWeather.HistoryWeather
import com.company.elverano.data.historyWeather.HistoryWeatherRepository
import com.company.elverano.data.historyWeather.HistoryWeatherResponse
import com.company.elverano.data.openWeather.OpenWeatherRepository
import com.company.elverano.data.openWeather.OpenWeatherResponse
import com.company.elverano.data.positionStack.PositionStackRepository
import com.company.elverano.utils.ResultEvent
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.request
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.lang.reflect.InvocationTargetException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject


@HiltViewModel
class SearchViewModel @Inject constructor(
    private val positionStackRepository: PositionStackRepository,
    private val openWeatherRepository: OpenWeatherRepository,
    private val historyWeatherRepository: HistoryWeatherRepository,
    private val errorRepository: ErrorRepository
) : ViewModel() {

    private var searchJob: Job? = null
    private var couritineJob: Job? = null
    private var messageJob: Job? = null

    var currentError = MutableLiveData<String>()

    private var _historyResponse = MutableLiveData<HistoryWeatherResponse>()
    val historyResponse: LiveData<HistoryWeatherResponse> get() = _historyResponse

    private val resultChannel = Channel<ResultEvent>()
    val resultEvent = resultChannel.receiveAsFlow()

    private var _weatherResponse = MutableLiveData<OpenWeatherResponse>()
    val weatherResponse: LiveData<OpenWeatherResponse> get() = _weatherResponse

    init {
        viewModelScope.launch {
            _weatherResponse.value = openWeatherRepository.getWeatherFromDB()
            _historyResponse.value = historyWeatherRepository.getLocationFromDatabase()
            downloadHistory()
        }
    }

    fun searchLocation(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {

            val oldPositionStack = positionStackRepository.getLocationFromDatabase()
            oldPositionStack?.let {
                if (!it.data.isNullOrEmpty()) {
                    updateHistory(weatherResponse.value, it.data[0].name!!)
                }
            }

            positionStackRepository.getLocationFromAPI(query)?.request { apiResponse ->
                when (apiResponse) {
                    is ApiResponse.Success -> {
                        messageJob?.cancel()
                        messageJob = viewModelScope.launch {
                            positionStackRepository.deletePositionFromDB()
                            positionStackRepository.insertPositionToDB(apiResponse.data)
                            apiResponse.data.data?.let { list ->
                                if (list.isNotEmpty()) {
                                    val item = list[0]
                                    searchWeather(
                                        lat = item.latitude!!,
                                        lon = item.longitude!!,
                                    )
                                } else {
                                    val msg = "No Item's found"
                                    resultChannel.send(ResultEvent.Error(msg))
                                    currentError.value = msg
                                }
                            } ?: kotlin.run {
                                val msg = "No Item's found"
                                resultChannel.send(ResultEvent.Error(msg))
                                currentError.value = msg
                            }
                        }
                    }
                    is ApiResponse.Failure.Error -> {
                        messageJob?.cancel()
                        messageJob = viewModelScope.launch {
                            val msg = "No Item's found\nError " + apiResponse.statusCode.code
                            resultChannel.send(ResultEvent.Error(msg))
                        }
                    }
                    is ApiResponse.Failure.Exception -> {
                        messageJob?.cancel()
                        messageJob = viewModelScope.launch {
                            when (apiResponse.exception) {
                                is UnknownHostException -> {
                                    val msg = "No Item's found\nNo Internet Connection!"
                                    resultChannel.send(ResultEvent.Error(msg))
                                }
                                is InvocationTargetException -> {
                                    val msg = "No Item's found!"
                                    resultChannel.send(ResultEvent.Error(msg))
                                }

                                is SocketTimeoutException -> {
                                    val msg = "No Item's found!"
                                    resultChannel.send(ResultEvent.Error(msg))
                                }
                                else -> {
                                    val msg =
                                        "No Item's found\nException: ${apiResponse.exception.message}"
                                    resultChannel.send(ResultEvent.Error(msg))
                                    throw apiResponse.exception
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun updateHistory(value: OpenWeatherResponse?, name: String) {
        value?.let { response ->
            val list = historyResponse.value?.data
            list?.let {
                val backup = it[0]
                it[1] = backup
                it[0] = HistoryWeather(
                    lat = response.lat,
                    lon = response.lon,
                    name = name,
                    weather_id = response.current!!.weather!![0].id,
                    temp = response.current.temp,
                    main = response.current.weather!![0].main,
                    description = response.current.weather[0].description,
                    icon = response.current.weather[0].icon,
                )

                historyResponse.value?.let {
                    historyWeatherRepository.deleteHistoryList()
                    historyWeatherRepository.insertResponseToDb(it)
                }
            }
        }
    }

    private suspend fun downloadHistory() {
        //Old weather is added to list. Two item's of search history  (not current weather)  will be displayed in Fragment
        historyWeatherRepository.getLocationFromDatabase().let {
            val first = it?.data?.get(0)
            first?.let { weather ->
                historyResponse.value?.data?.set(0, weather)
            }

            val second = it?.data?.get(1)
            second?.let { weather ->
                historyResponse.value?.data?.set(1, weather)
            }
        }
    }

    private fun searchWeather(lat: Double, lon: Double) {
        couritineJob?.cancel()
        couritineJob = viewModelScope.launch {
            openWeatherRepository.getWeatherFromAPI(lon = lon, lat = lat)?.request {
                when (it) {
                    is ApiResponse.Success -> {
                        messageJob?.cancel()
                        messageJob = viewModelScope.launch {
                            openWeatherRepository.deleteWeatherFromDatabase()
                            openWeatherRepository.insertWeatherToDatabase(it.data)
                            resultChannel.send(ResultEvent.Success)
                        }
                    }

                    is ApiResponse.Failure.Error -> {
                        messageJob?.cancel()
                        messageJob = viewModelScope.launch {
                            val msg = "No Item's found\nError " + it.statusCode.code
                            resultChannel.send(ResultEvent.Error(msg))
                        }
                    }

                    is ApiResponse.Failure.Exception -> {
                        messageJob?.cancel()
                        messageJob = viewModelScope.launch {
                            when (it.exception) {
                                is UnknownHostException -> {
                                    val msg = "No Item's found\nNo Internet Connection!"
                                    resultChannel.send(ResultEvent.Error(msg))
                                }
                                else -> {
                                    val msg =
                                        "No Item's found\nException: ${it.exception.message}"
                                    resultChannel.send(ResultEvent.Error(msg))
                                    throw it.exception
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    fun insertError(customError: CustomError) = viewModelScope.launch {
        errorRepository.deleteErrorFromDatabase()
        errorRepository.insertErrorToDatabase(customError)
    }

    fun deleteError() = viewModelScope.launch {
        errorRepository.deleteErrorFromDatabase()
    }
}