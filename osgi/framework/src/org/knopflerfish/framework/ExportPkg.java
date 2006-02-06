/*
 * Copyright (c) 2005-2006, KNOPFLERFISH project
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

import java.util.*;

import org.osgi.framework.Constants;
import org.osgi.framework.Version;


/**
 * Data structure for export package definitions.
 *
 * @author Jan Stein
 */
class ExportPkg {
  final String name;
  final BundleImpl bundle;
  final ArrayList /* String */ uses;
  final ArrayList /* String */ mandatory;
  final ArrayList /* String */ include;
  final ArrayList /* String */ exclude;
  final Version version;
  final Map attributes;
  boolean zombie = false;

  // Link to pkg entry
  Pkg pkg = null;

  /**
   * Create an export package entry.
   */
  ExportPkg(String name, Map tokens, BundleImpl b) {
    this.bundle = b;
    this.name = name;
    if (name.startsWith("java.")) {
      throw new IllegalArgumentException("You can not export a java.* package");
    }
    this.uses = Util.parseEnumeration(Constants.USES_DIRECTIVE,
				      (String)tokens.remove(Constants.USES_DIRECTIVE));
    this.mandatory = Util.parseEnumeration(Constants.MANDATORY_DIRECTIVE,
					   (String)tokens.remove(Constants.MANDATORY_DIRECTIVE));
    this.include = Util.parseEnumeration(Constants.INCLUDE_DIRECTIVE,
					 (String)tokens.remove(Constants.INCLUDE_DIRECTIVE));
    this.exclude = Util.parseEnumeration(Constants.EXCLUDE_DIRECTIVE,
					 (String)tokens.remove(Constants.EXCLUDE_DIRECTIVE));
    String versionStr = (String)tokens.remove(Constants.VERSION_ATTRIBUTE);
    String specVersionStr = (String)tokens.remove(Constants.PACKAGE_SPECIFICATION_VERSION);
    if (specVersionStr != null) {
      this.version = new Version(specVersionStr);
      if (versionStr != null && !this.version.equals(new Version(versionStr))) {
	throw new IllegalArgumentException("Both " + Constants.VERSION_ATTRIBUTE + 
                                           "and " + Constants.PACKAGE_SPECIFICATION_VERSION +
					   "are specified, and differs");
      }
    } else if (versionStr != null) {
      this.version = new Version(versionStr);
    } else {
      this.version = Version.emptyVersion;
    }
    if (tokens.containsKey(Constants.BUNDLE_VERSION_ATTRIBUTE)) {
      throw new IllegalArgumentException("Export definition illegally contains attribute, " +
					 Constants.BUNDLE_VERSION_ATTRIBUTE);
    }
    if (tokens.containsKey(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE)) {
      throw new IllegalArgumentException("Export definition illegally contains attribute, " +
					 Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE);
    }
    this.attributes = tokens;
  }


  /**
   * Create an export package entry with a new name from an export template.
   */
  ExportPkg(ExportPkg ep, String name) {
    this.name = name;
    this.bundle = ep.bundle;
    this.uses = ep.uses;
    this.mandatory = ep.mandatory;
    this.include = ep.include;
    this.exclude = ep.exclude;
    this.version = ep.version;
    this.attributes = ep.attributes;
  }


  /**
   * Create a re-export package entry with a new bundle owner from an existing export.
   */
  ExportPkg(ExportPkg ep, BundleImpl b) {
    this.name = ep.name;
    this.bundle = b;
    this.uses = ep.uses;
    this.mandatory = ep.mandatory;
    this.include = ep.include;
    this.exclude = ep.exclude;
    this.version = ep.version;
    this.attributes = ep.attributes;
  }


  /**
   * Checks if we are allowed to export this class according to
   * the filter rules.
   */
  boolean checkFilter(String fullClassName) {
    String clazz = null;
    boolean ok = true;
    if (fullClassName != null) {
      if (include != null) {
        // assert fullClassName.startsWith(name)
        clazz = fullClassName.substring(name.length() + 1);
        for (Iterator i = include.iterator(); i.hasNext(); ) {
          if (filterMatch((String)i.next(), clazz)) {
            break;
          }
          if (!i.hasNext()) {
            ok = false;
          }
        }
      }
      if (ok && exclude != null) {
        if (clazz == null) {
          // assert fullClassName.startsWith(name)
          clazz = fullClassName.substring(name.length() + 1);
        }
        for (Iterator i = exclude.iterator(); i.hasNext(); ) {
          if (filterMatch((String)i.next(), clazz)) {
            ok = false;
            break;
          }
        }
      }
    }
    return ok;
  }


  /**
   */
  private  boolean filterMatch(String filter, String s) {
    return patSubstr(s.toCharArray(), 0, filter.toCharArray(), 0);
  }


  /**
   */
  private boolean patSubstr(char[] s, int si, char[] pat, int pi) {
    if (pat.length-pi == 0) 
      return s.length-si == 0;
    if (pat[pi] == '*') {
      pi++;
      for (;;) {
        if (patSubstr( s, si, pat, pi))
          return true;
        if (s.length-si == 0)
          return false;
        si++;
      }
    } else {
      if (s.length-si==0){
        return false;
      }
      if(s[si]!=pat[pi]){
        return false;
      }
      return patSubstr( s, ++si, pat, ++pi);
    }
  }


  /**
   * String describing package name and specification version, if specified.
   *
   * @return String.
   */
  public String pkgString() {
    if (version != Version.emptyVersion) {
      return name + ";" + Constants.PACKAGE_SPECIFICATION_VERSION + "=" + version;
    } else {
      return name;
    }
  }


  /**
   * String describing this object.
   *
   * @return String.
   */
  public String toString() {
    return pkgString() + "(" + bundle + ")";
  }

}
