package com.solidsketchapp.core.api

import java.io.OutputStream

/** Saving exports (PDF, DXF, DWG, CSV, IFC, NC zip) to Downloads/SolidSketch. */
interface FileApi {
    /** Opens [fileName] for writing in the export folder; returns a user-visible location. */
    fun export(fileName: String, mimeType: String, write: (OutputStream) -> Unit): String
}
