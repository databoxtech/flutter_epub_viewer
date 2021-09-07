/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.db.*
import org.joda.time.DateTime
import org.json.JSONObject
import org.readium.r2.shared.extensions.toPng
import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.asset.PublicationAsset
import java.net.URI
import java.nio.file.Paths
import kotlin.math.min

/**
 * Global Parameters
 */

lateinit var books: MutableList<Book>


// Access property for Context
val Context.database: BooksDatabaseOpenHelper
    get() = BooksDatabaseOpenHelper.getInstance(applicationContext)

val Context.appContext: Context
    get() = applicationContext

class Book(var id: Long? = null,
           val creation: Long = DateTime().toDate().time,
           val identifier: String,
           val progression: String? = null
) {

}

class BooksDatabase(context: Context) {

    val shared: BooksDatabaseOpenHelper = BooksDatabaseOpenHelper(context)
    var books: BOOKS

    init {
        books = BOOKS(shared)
    }

}

class BooksDatabaseOpenHelper(ctx: Context) : ManagedSQLiteOpenHelper(ctx, "books_database", null, DATABASE_VERSION) {
    companion object {
        private var instance: BooksDatabaseOpenHelper? = null
        private const val DATABASE_VERSION = 3

        @Synchronized
        fun getInstance(ctx: Context): BooksDatabaseOpenHelper {
            if (instance == null) {
                instance = BooksDatabaseOpenHelper(ctx.applicationContext)
            }
            return instance!!
        }
    }

    override fun onCreate(db: SQLiteDatabase) {

        db.createTable(BOOKSTable.NAME, true,
                BOOKSTable.ID to INTEGER + PRIMARY_KEY + AUTOINCREMENT,
                BOOKSTable.IDENTIFIER to TEXT,
                BOOKSTable.CREATION to INTEGER,
                BOOKSTable.PROGRESSION to TEXT)

    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Here you can upgrade tables, as usual
        // migration = add extension column
        when (oldVersion) {
            1 -> {
                try {
                    upgradeVersion2(db) {
                        //done
                    }
                } catch (e: SQLiteException) {
                }
                try {
                    upgradeVersion3(db) {
                        //done
                    }
                } catch (e: SQLiteException) {
                }
            }
            2 -> {
                try {
                    upgradeVersion3(db) {
                        //done
                    }
                } catch (e: SQLiteException) {
                }
            }
        }
    }

    private fun upgradeVersion2(db: SQLiteDatabase, callback: () -> Unit) {
//        db.execSQL("ALTER TABLE " + BOOKSTable.NAME + " ADD COLUMN " + BOOKSTable.EXTENSION + " TEXT DEFAULT '.epub';")
//        val cursor = db.query(BOOKSTable.NAME, BOOKSTable.RESULT_COLUMNS, null, null, null, null, null, null)
//        if (cursor != null) {
//            var hasItem = cursor.moveToFirst()
//            while (hasItem) {
//                val id = cursor.getInt(cursor.getColumnIndex(BOOKSTable.ID))
//                val values = ContentValues()
//                values.put(BOOKSTable.EXTENSION, Publication.EXTENSION.EPUB.value)
//                db.update(BOOKSTable.NAME, values, "${BOOKSTable.ID}=?", arrayOf(id.toString()))
//                hasItem = cursor.moveToNext()
//            }
//            cursor.close()
//        }
        callback()
    }

    private fun upgradeVersion3(db: SQLiteDatabase, callback: () -> Unit) {
        db.execSQL("ALTER TABLE " + BOOKSTable.NAME + " ADD COLUMN " + BOOKSTable.CREATION + " INTEGER DEFAULT ${DateTime().toDate().time};")
        val cursor = db.query(BOOKSTable.NAME, BOOKSTable.RESULT_COLUMNS, null, null, null, null, null, null)
        if (cursor != null) {
            var hasItem = cursor.moveToFirst()
            while (hasItem) {
                val id = cursor.getInt(cursor.getColumnIndex(BOOKSTable.ID))
                val values = ContentValues()
                values.put(BOOKSTable.CREATION, DateTime().toDate().time)
                db.update(BOOKSTable.NAME, values, "${BOOKSTable.ID}=?", arrayOf(id.toString()))
                hasItem = cursor.moveToNext()
            }
            cursor.close()
        }
        callback()
    }

}

object BOOKSTable {
    const val NAME = "BOOKS"
    const val ID = "id"
    const val IDENTIFIER = "identifier"
    const val CREATION = "creationDate"
    const val PROGRESSION = "progression"
    var RESULT_COLUMNS = arrayOf(ID, IDENTIFIER, CREATION, PROGRESSION)

}

class BOOKS(private var database: BooksDatabaseOpenHelper) {

    fun dropTable() {
        database.use {
            dropTable(BOOKSTable.NAME, true)
        }
    }

    fun insert(book: Book, allowDuplicates: Boolean): Long? {
        val exists = has(book)
        if (exists.isEmpty() || allowDuplicates) {
            // Makes sure the cover is not too large, to prevent SQLiteBlobTooBigException.


            return database.use {
                return@use insert(BOOKSTable.NAME,
                        BOOKSTable.ID to book.id,
                        BOOKSTable.IDENTIFIER to book.identifier
                )
            }
        }
        return null
    }

    private fun has(book: Book): List<Book> {
        return database.use {
            select(BOOKSTable.NAME, BOOKSTable.ID, BOOKSTable.IDENTIFIER, BOOKSTable.CREATION, BOOKSTable.PROGRESSION)
                    .whereArgs("identifier = {identifier}", "identifier" to book.identifier)
                    .exec {
                        parseList(MyRowParser())
                    }
        }
    }

    fun has(identifier: String): List<Book> {
        return database.use {
            select(BOOKSTable.NAME, BOOKSTable.ID, BOOKSTable.IDENTIFIER,  BOOKSTable.CREATION, BOOKSTable.PROGRESSION)
                    .whereArgs("identifier = {identifier}", "identifier" to identifier)
                    .exec {
                        parseList(MyRowParser())
                    }
        }
    }

    private fun has(id: Long): List<Book> {
        return database.use {
            select(BOOKSTable.NAME, BOOKSTable.ID, BOOKSTable.IDENTIFIER, BOOKSTable.CREATION, BOOKSTable.PROGRESSION)
                    .whereArgs("id = {id}", "id" to id)
                    .exec {
                        parseList(MyRowParser())
                    }
        }
    }

    fun currentLocator(id: Long): Locator? {
        return database.use {
            select(BOOKSTable.NAME, BOOKSTable.ID, BOOKSTable.IDENTIFIER, BOOKSTable.CREATION, BOOKSTable.PROGRESSION)
                    .whereArgs("id = {id}", "id" to id)
                    .exec {
                        parseList(MyRowParser()).firstOrNull()?.progression?.let {
                            Locator.fromJSON(JSONObject(it))
                        } ?: run {
                            null
                        }
                    }
        }
    }

    fun delete(book: Book): Int {
        return database.use {
            return@use delete(BOOKSTable.NAME, "id = {id}", "id" to book.id!!)
        }
    }

    fun list(): MutableList<Book> {
        return database.use {
            select(BOOKSTable.NAME, BOOKSTable.ID, BOOKSTable.IDENTIFIER, BOOKSTable.CREATION, BOOKSTable.PROGRESSION)
                    .orderBy(BOOKSTable.CREATION, SqlOrderDirection.DESC)
                    .exec {
                        parseList(MyRowParser()).toMutableList()
                    }
        }
    }

    fun saveProgression(locator: Locator?, bookId: Long): Boolean {
        val exists = has(bookId)
        if (exists.isEmpty()) {
            return false
        }
        return database.use {
            return@use update(BOOKSTable.NAME, BOOKSTable.PROGRESSION to locator?.toJSON().toString())
                    .whereArgs("${BOOKSTable.ID} = {id}", "id" to bookId)
                    .exec() > 0
        }
    }

    class MyRowParser : RowParser<Book> {
        override fun parseRow(columns: Array<Any?>): Book {

            val id = columns[0]?.let {
                return@let it as Long
            } ?: kotlin.run { return@run (-1).toLong() }

            val identifier = columns[1]?.let {
                return@let it as String
            } ?: kotlin.run { return@run "" }

            val creation: Int = columns[2]?.let {
                return@let it as Int
            } ?: kotlin.run { return@run 0 }

            val progression = columns[3]?.let {
                return@let it as String
            } ?: kotlin.run { return@run null }

            return Book(id, 0L, identifier, progression)
        }
    }
}

private fun Bitmap.scaleToFit(maxSize: Size): Bitmap {
    if (width <= maxSize.width && height <= maxSize.height)
        return this

    val ratio = min(
        maxSize.width / width.toFloat(),
        maxSize.height / height.toFloat()
    )

    val newWidth = (ratio * width).toInt()
    val newHeight = (ratio * height).toInt()

    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}