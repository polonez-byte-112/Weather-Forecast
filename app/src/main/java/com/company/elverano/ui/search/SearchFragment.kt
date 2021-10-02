package com.company.elverano.ui.search

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.company.elverano.R
import com.company.elverano.data.openWeather.OpenWeatherResponse
import com.company.elverano.databinding.FragmentSearchBinding
import com.company.elverano.utils.ResultEvent
import com.company.elverano.utils.formatDoubleString
import com.company.elverano.utils.setWeatherIcon
import dagger.hilt.android.AndroidEntryPoint
import jp.wasabeef.blurry.Blurry
import kotlinx.coroutines.flow.collect


@AndroidEntryPoint
class SearchFragment : Fragment(R.layout.fragment_search) {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel by viewModels<SearchViewModel>()

    companion object {
        const val RADIUS = 5
        const val SMALL_RADIUS = 1
        const val SAMPLING = 8
        const val SMALL_SAMPLING = 2
        const val ANIM_DURATION = 500
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSearchBinding.bind(view)


        viewModel.weatherResponse.observe(viewLifecycleOwner) {
            updateUI(it)
        }

        addSearchViewListener()


        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            collectResults()
        }
    }

    private fun initializeBlur() {
        Blurry.with(context)
            .radius(RADIUS)
            .sampling(SAMPLING)
            .async().animate(ANIM_DURATION)
            .onto(binding.historyItemOne)

        Blurry.with(context)
            .radius(RADIUS)
            .sampling(SAMPLING)
            .async().animate(ANIM_DURATION)
            .onto(binding.historyItemTwo)

        Blurry.with(context)
            .radius(SMALL_RADIUS)
            .sampling(SMALL_SAMPLING)
            .async().animate(ANIM_DURATION)
            .onto(binding.searchCurrentDetailsBox)
    }

    private suspend fun collectResults() {
        viewModel.resultEvent.collect { event ->
            when (event) {
                is ResultEvent.Success -> {
                    Log.d("ResultEvent", "Success")
                    val action =
                        SearchFragmentDirections.actionSearchFragmentToCurrentFragment()
                    findNavController().navigate(action)
                    binding.searchProgressBar.visibility = View.INVISIBLE
                }
                is ResultEvent.Error -> {
                    binding.apply {
                        binding.searchProgressBar.visibility = View.INVISIBLE
                    }
                    Log.d("ResultEvent", "Error")
                }
            }
        }
    }

    private fun addSearchViewListener() {
        binding.apply {
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {

                    if (query != null) {
                        searchProgressBar.visibility = View.VISIBLE
                        viewModel.searchLocation(query)
                        searchView.setQuery("", false)
                        searchView.clearFocus()
                        searchView.onActionViewCollapsed()
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    return true
                }

            })
        }
    }

    private fun updateUI(weather: OpenWeatherResponse) {
        binding.apply {

            searchCurrentHumidity.text = "${weather.current.humidity} %"
            searchCurrentPressure.text = "${weather.current.pressure}hPa"
            searchCurrentWindSpeed.text = "${weather.current.wind_speed} km/h"
            searchCurrentWindDegrees.text = "${weather.current.wind_deg} ∡"

            view?.post {
                initializeBlur()
            }

            val isNight = weather.current.getNight()
            isNight?.let {
                val drawable =
                    setWeatherIcon(
                        weather.current.weather[0].id,
                        isNight,
                        resources
                    )

                context?.let {
                    Glide.with(it)
                        .load(drawable)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(searchCurrentImg)
                }




            }


            //Initialize forecast data
          val firstItem =   viewModel.weatherList[0]
          val secondItem = viewModel.weatherList[1]

            historyCityNameOne.text = firstItem.name
            historyCityTempOne.text   = formatDoubleString(firstItem.current.temp,0)
            historyCityMainOne.text = firstItem.current.weather[0].main
            val isNight_first = firstItem.current.getNight()
            isNight_first?.let {
                val drawable =
                    setWeatherIcon(
                        firstItem.current.weather[0].id,
                        it,
                        resources
                    )

                context?.let {
                    Glide.with(it)
                        .load(drawable)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(historyCityImgOne)
                }

            }

            historyCityNameTwo.text = secondItem.name
            historyCityTempTwo.text  = formatDoubleString(secondItem.current.temp,0)
            historyCityMainTwo.text = secondItem.current.weather[0].main
            val isNight_second = secondItem.current.getNight()
            isNight_second?.let {
                val drawable =
                    setWeatherIcon(
                        secondItem.current.weather[0].id,
                        it,
                        resources
                    )

                context?.let {
                    Glide.with(it)
                        .load(drawable)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(historyCityImgTwo)
                }

            }


        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}