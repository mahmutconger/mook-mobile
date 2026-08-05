package com.mcclabs.mook.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore

/**
 * Single, shared Firestore instance for the whole app.
 *
 * gitlive's iOS wrapper re-applies [FirebaseFirestoreSettings] every time the
 * `Firebase.firestore` getter is accessed (`native.settings = ...` in its
 * constructor). Firestore forbids changing settings once the instance has been
 * used, so repeated `Firebase.firestore` calls crash on iOS with
 * `FIRIllegalStateException: Firestore instance has already been started`.
 *
 * Accessing this lazily-created value once means settings are applied a single
 * time, before any operation. Use [appFirestore] everywhere instead of
 * `Firebase.firestore`.
 */
val appFirestore: FirebaseFirestore by lazy { Firebase.firestore }
