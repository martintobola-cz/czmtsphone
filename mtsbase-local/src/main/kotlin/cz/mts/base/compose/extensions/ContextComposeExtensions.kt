package cz.mts.base.compose.extensions

import android.content.Context
import cz.mts.base.helpers.BaseConfig

val Context.config: BaseConfig get() = BaseConfig.newInstance(applicationContext)
