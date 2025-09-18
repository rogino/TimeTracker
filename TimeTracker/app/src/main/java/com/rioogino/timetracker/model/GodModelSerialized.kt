package com.rioogino.timetracker.model

import android.content.Context
import android.util.Log
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rioogino.timetracker.TAG
import com.rioogino.timetracker.minusDays
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant

fun GodModel.serialize(maxEntryAge: Long = 3): GodModelSerialized {
    val now = Instant.now()
    val entries = timeEntries.filter { now.minusDays(maxEntryAge).isBefore(it.startTime) }
    return GodModelSerialized(tags, projects.values, entries)
}

class GodModelSerialized(
    val tags: Collection<String>,
    val projects: Collection<Project>,
    var entries: Collection<TimeEntry>
) {
    fun populateModelIfEmpty(model: GodModel = GodModel()): GodModel {
        if (model.timeEntries.isEmpty()) {
            Log.d(TAG, "Add ${entries?.count()} entries from disk to the model")
            model.setEntries(entries)
        }
        if (model.projects.isEmpty()) {
            Log.d(TAG, "Add ${projects?.count()} projects from disk to the model")
            model.setProjects(projects)
        }
        if (model.tags.isEmpty()) {
            Log.d(TAG, "Add ${tags?.count()} tags from disk to the model")
            model.setTags(tags)
        }

        return model
    }

    companion object {
        val FILE_NAME = "model.json"
        val MAX_ENTRY_AGE: Long = 3
        val jsonConverter = Klaxon().converter(DateTimeConverter)

        suspend fun saveModelToFile(context: Context, model: GodModel) {
            var newModel: GodModelSerialized
            withContext(Dispatchers.Main) {
                newModel = model.serialize()
                val now = Instant.now()
                newModel.entries = newModel.entries.filter {
                    now.minusDays(MAX_ENTRY_AGE).isBefore(it.startTime)
                }
            }
            saveModelToFile(context, newModel)
        }

        suspend fun saveModelToFile(context: Context, model: GodModelSerialized) {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Serializing model to JSON")
                val serialized = jsonConverter.toJsonString(model)
                Log.d(TAG, "Finished serializing model to JSON (len ${serialized.length})")
                var file: FileOutputStream? = null
                try {
                    Log.d(TAG, "Writing $FILE_NAME to disk")
                    file = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)
                    file.write(serialized.toByteArray())
                    Log.d(TAG, "Finished writing $FILE_NAME to disk")
                } catch (err: Throwable) {
                    Log.d(TAG, "Failed to write $FILE_NAME to disk or serialize it")
                    Log.d(TAG, err.stackTraceToString())
                } finally {
                    file?.close()
                }
            }
        }

        suspend fun readAndPopulateModel(
            context: Context,
            model: GodModel
        ) {
            withContext(Dispatchers.IO) {
                var file: FileInputStream? = null
                try {
                    Log.d(TAG, "Reading $FILE_NAME from disk")
                    file = context.openFileInput(FILE_NAME)
                    val newModel = jsonConverter.parse<GodModelSerialized>(file)
                    Log.d(TAG, "Finished reading $FILE_NAME from disk")
                    if (newModel == null) return@withContext;

                    val now = Instant.now()
                    newModel.entries = newModel.entries.filter {
                        now.minusDays(MAX_ENTRY_AGE).isBefore(it.startTime)
                    }

                    withContext(Dispatchers.Main) {
                        newModel.populateModelIfEmpty(model)
                    }
                } catch (err: Throwable) {
                    Log.d(TAG, "Failed to read $FILE_NAME from disk or parse it as JSON")
                    Log.d(TAG, err.stackTraceToString())
                    null
                } finally {
                    file?.close()
                }
            }
        }

        suspend fun clear(context: Context) {
            Log.d(TAG, "Deleting $FILE_NAME")
            withContext(Dispatchers.IO) {
                context.deleteFile(FILE_NAME)
                Log.d(TAG, "Deleted $FILE_NAME")
            }
        }
    }
}
