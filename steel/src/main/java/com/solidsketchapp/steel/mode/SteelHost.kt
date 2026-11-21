package com.solidsketchapp.steel.mode

import com.solidsketchapp.core.api.BooleanEngine
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.FileApi
import com.solidsketchapp.core.api.LicenseApi
import com.solidsketchapp.core.api.SelectionApi
import com.solidsketchapp.core.api.UndoApi
import com.solidsketchapp.core.api.ViewportApi

/**
 * Everything Steel gets from the host app, as :core-api interfaces only.
 * In S0 most are not wired yet (null); they are filled in S2/S3.
 */
class SteelHost(
    val license: LicenseApi,
    val undo: UndoApi? = null,
    val document: DocumentApi? = null,
    val viewport: ViewportApi? = null,
    val selection: SelectionApi? = null,
    val booleans: BooleanEngine? = null,
    val files: FileApi? = null,
)
