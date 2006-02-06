/*
 * Copyright (c) 2006, KNOPFLERFISH project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials
 *   provided with the distribution.
 *
 * - Neither the name of the KNOPFLERFISH project nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.knopflerfish.framework;

import org.osgi.framework.Bundle;
import org.osgi.service.packageadmin.RequiredBundle;
import org.osgi.framework.Version;

/**
 * Implementation for required bundle interface.
 * 
 */
public class RequiredBundleImpl implements RequiredBundle
{

  /**
   */
  private BundleImpl bundle;

  private long lastModified;

  /**
   *
   */
  RequiredBundleImpl(BundleImpl b) {
    bundle = b;
    lastModified = b.lastModified;
  }


  /**
   * Returns the symbolic name of this required bundle.
   * 
   * @return The symbolic name of this required bundle.
   */
  public String getSymbolicName() {
    return bundle.symbolicName;
  }


  /**
   * Returns the bundle associated with this required bundle.
   * 
   * @return The bundle, or <code>null</code> if this
   *         <code>RequiredBundle</code> object has become stale.
   */
  public Bundle getBundle() {
    if (isRemovalPending()) {
      return (Bundle)bundle;
    } else {
      return null;
    }
  }


  /**
   * Returns the bundles that currently require this required bundle.
   * 
   * <p>
   * If this required bundle is required and then re-exported by another
   * bundle then all the requiring bundles of the re-exporting bundle are
   * included in the returned array.
   * 
   * @return An array of bundles currently requiring this required bundle, or
   *         <code>null</code> if this <code>RequiredBundle</code> object
   *         has become stale.
   */
  public Bundle[] getRequiringBundles() {
    if (isRemovalPending()) {
      int size = bundle.requiredBy.size();
      if (size > 0) {
        return (Bundle[])bundle.requiredBy.toArray(new Bundle[size]);
      }
    }
    return null;
  }


  /**
   * Returns the version of this required bundle.
   * 
   * @return The version of this required bundle, or
   *         {@link Version#emptyVersion} if no version information is
   *         available.
   */
  public Version getVersion() {
    return bundle.version;
  }


  /**
   * Returns <code>true</code> if the bundle associated with this
   * <code>RequiredBundle</code> object has been updated or uninstalled.
   * 
   * @return <code>true</code> if the reqiured bundle has been updated or
   *         uninstalled, or if the <code>RequiredBundle</code> object has
   *         become stale; <code>false</code> otherwise.
   */
  public boolean isRemovalPending() {
    // NYI! Fix a more stable version.
    return bundle.lastModified != lastModified;
  }

}