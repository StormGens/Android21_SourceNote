/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.support.test;

import android.app.Instrumentation;
import android.content.Context;

import junit.framework.TestSuite;


/**
 * Placeholder suite to verify {@link Context} and {@link Instrumentation} injection for suites.
 * <p/>
 * Need to explicitly run via adb shell am instrument -w \
 * -e class android.support.test.MyTestSuiteBuilder \
 * android.support.test.tests/android.support.test.runner.AndroidJUnitRunner
 */
public class MyTestSuiteBuilder {

    public static TestSuite suite() {
        TestSuite ts = new TestSuite();
        ts.addTestSuite(MyAndroidTestCase.class);
        ts.addTestSuite(MyInstrumentationTestCase.class);
        ts.addTestSuite(MyBundleTestCase.class);
        return ts;
    }
}
