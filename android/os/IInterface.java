/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.os;

/**
 * Base class for Binder interfaces.  When defining a new interface,
 * you must derive it from IInterface.
 * 【汉】Binder接口的基类，当定义一个新的（Binder）接口时候，必须从IInterface定义它。
 */
public interface IInterface
{
    /**
     * Retrieve the Binder object associated with this interface.
     * 【汉】重新得到和这个接口关联的binder对象
     * You must use this instead of a plain cast, so that proxy objects
     * can return the correct result.
     * 【汉】你必须使用这个方法替代简单的cast，代理对象才能返回正确的结果。
     */
    public IBinder asBinder();
}
