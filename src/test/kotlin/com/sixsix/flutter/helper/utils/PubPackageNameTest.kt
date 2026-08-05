package com.sixsix.flutter.helper.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PubPackageNameTest {

    @Test
    fun matchesVersionedDependency() {
        assertTrue("provider: ^6.0.5".isPubPackageName())
        assertTrue("  cupertino_icons: ^1.0.8".isPubPackageName())
        assertTrue("http: >=1.0.0 <2.0.0".isPubPackageName())
    }

    @Test
    fun rejectsSdkVersionRefAndComments() {
        assertFalse("sdk: flutter".isPubPackageName())
        assertFalse("version: 1.0.0".isPubPackageName())
        assertFalse("ref: main".isPubPackageName())
        assertFalse("dependencies:".isPubPackageName())
        assertFalse("# provider: ^6.0.0".isPubPackageName())
    }
}
