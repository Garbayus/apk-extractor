package com.example.apkextractor.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface DataRepository {
  fun getApps(context: Context): Flow<List<AppInfo>>
}

class DefaultDataRepository : DataRepository {
  override fun getApps(context: Context): Flow<List<AppInfo>> = flow {
    emit(ApkExtractor.getInstalledApps(context))
  }.flowOn(Dispatchers.IO)
}
