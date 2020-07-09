/*
 * Copyright (c) 2020 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.location.ui

import android.webkit.GeolocationPermissions
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import kotlinx.coroutines.launch

class LocationPermissionsViewModel(
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val settingsDataStore: SettingsDataStore
) : SiteLocationPermissionDialog.Listener, ViewModel() {

    data class ViewState(
        val locationPermissionEnabled: Boolean = false,
        val locationPermissionEntities: List<LocationPermissionEntity> = emptyList()
    )

    sealed class Command {
        class ConfirmDeleteLocationPermission(val entity: LocationPermissionEntity) : Command()
    }

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData()
    val viewState: LiveData<ViewState> = _viewState
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val locationPermissions: LiveData<List<LocationPermissionEntity>> = locationPermissionsRepository.getLocationPermissionsAsync()
    private val fireproofWebsitesObserver = Observer<List<LocationPermissionEntity>> { onLocationPermissionsEntitiesChanged(it!!) }

    init {
        _viewState.value = ViewState(
            locationPermissionEnabled = settingsDataStore.appLocationPermission
        )
        locationPermissions.observeForever(fireproofWebsitesObserver)
    }

    override fun onCleared() {
        super.onCleared()
        locationPermissions.removeObserver(fireproofWebsitesObserver)
    }

    private fun onLocationPermissionsEntitiesChanged(entities: List<LocationPermissionEntity>) {
        _viewState.value = _viewState.value?.copy(
            locationPermissionEntities = entities
        )
    }

    fun onDeleteRequested(entity: LocationPermissionEntity) {
        command.value = Command.ConfirmDeleteLocationPermission(entity)
    }

    fun delete(entity: LocationPermissionEntity) {
        viewModelScope.launch(dispatcherProvider.io()) {
            locationPermissionsRepository.removeLocationPermission(entity.domain)
            val geolocationPermissions = GeolocationPermissions.getInstance()
            geolocationPermissions.clear(entity.domain)
        }
    }

    fun onLocationPermissionToggled(enabled: Boolean) {
        settingsDataStore.appLocationPermission = enabled
        _viewState.value = _viewState.value?.copy(locationPermissionEnabled = enabled)
    }

    override fun onSiteLocationPermissionSelected(domain: String, permission: LocationPermissionType) {
        viewModelScope.launch {
            locationPermissionsRepository.saveLocationPermission(domain, permission)
        }
    }
}