/*
 * Copyright (c) 2003-2009, KNOPFLERFISH project
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

package org.knopflerfish.framework.bundlestorage.file;

import org.knopflerfish.framework.*;
import org.osgi.framework.*;
import java.io.*;
import java.net.*;
import java.security.cert.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;


/**
 * JAR file handling.
 *
 * @author Jan Stein
 * @author Philippe Laporte
 * @author Mats-Ola Persson
 */
class Archive {

  /**
   * Base for filename used to store copy of archive.
   */
  final private static String ARCHIVE = "jar";

  /**
   * Directory base name to use for sub-archives.
   */
  final private static String SUBDIR = "sub";

  /**
   * File suffix for certificates.
   */
  final private static String CERTS_SUFFIX = ".crt";

  /**
   * Directory where optional bundle files are stored
   * these need not be unpacked locally
   */
  final private static String META_INF_DIR = "META-INF/";

  /**
   * Directory where optional bundle files are stored
   * these need not be unpacked locally
   */
  final private static String OSGI_OPT_DIR = "OSGI-OPT/";

  /**
   * Bundle entry where local permissions are stored.
   */
  final private static String LOCAL_PERMISSION_FILE = "OSGI-INF/permissions.perm";

  /**
   * Property base string.
   */
  final private static String PROP_BASE = "org.knopflerfish.framework.bundlestorage.file.";

  /**
   * Controls if we should try to unpack bundles with sub-jars and
   * native code.
   */
  final private static boolean unpack =
    new Boolean(Framework.getProperty(PROP_BASE + "unpack", "true")).booleanValue();

  /**
   * Controls if we should try to unpack bundles with sub-jars and
   * native code.
   */
  final private static boolean alwaysUnpack =
    new Boolean(Framework.getProperty(PROP_BASE + "always_unpack", "false")).booleanValue();

  /**
   * Controls if file: URLs should be referenced only, not copied
   * to bundle storage dir
   */
  final private static boolean fileReference =
    new Boolean(Framework.getProperty(PROP_BASE + "reference", "false")).booleanValue();

  /**
   * Controls if we should trust file storage to be secure.
   */
  final private static boolean trustedStorage =
    new Boolean(Framework.getProperty(PROP_BASE + "trusted", "true")).booleanValue();

  /**
   * File handle for file that contains current archive.
   */
  private FileTree file;

  /**
   * Set to true if above file is a reference outside framework storage.
   */
  private boolean fileIsReference;

  /**
   * JAR file handle for file that contains current archive.
   */
  private ZipFile jar;


  final private String location;

  /**
   * Certificates for this archive.
   */
  private Certificate [] certs = null;

  /**
   * Archive's manifest
   */
  Manifest manifest /*= null*/;

  /**
   * JAR Entry handle for file that contains current archive.
   * If not null, it is a sub jar instead.
   */
  private ZipEntry subJar /*= null*/;

  /**
   * Is Archive closed.
   */
  private boolean bClosed = false;



  /**
   * Create an Archive based on contents of an InputStream,
   * the archive is saved as local copy in the specified
   * directory.
   *
   * @param storage BundleStorageImpl for this archive.
   * @param dir Directory to save data in.
   * @param rev Revision of bundle content (used for updates).
   * @param is Jar file data in an InputStream.
   * @param url URL to use to CodeSource.
   * @param location Location for archive
   */
  Archive(File dir, int rev, InputStream is, URL source, String location, boolean checkSigned)
    throws IOException, BundleException
  {
    this.location = location;
    boolean isDirectory = false;
    final FileTree sourceFile;
    fileIsReference = isReference(source);

    if (fileIsReference) {
      sourceFile = new FileTree(getFile(source));
      file = sourceFile;
    } else {
      sourceFile = isFile(source) ? new FileTree(getFile(source)) : null;
      file = new FileTree(dir, ARCHIVE + rev);
    }
    if (sourceFile != null) {
      isDirectory = sourceFile.isDirectory();
      if (isDirectory) {
        File mfd = new File(sourceFile.getAbsolutePath(), META_INF_DIR);
        File mf = new File(mfd, "MANIFEST.MF");
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(mf));
        try {
          manifest = new Manifest(bis);
        } finally {
          bis.close();
        }
      }
    }

    BufferedInputStream bis =  null;
    JarInputStream ji = null;
    if (manifest == null) {
      bis = new BufferedInputStream(is);
      if (alwaysUnpack) {
        ji = new JarInputStream(bis, checkSigned);
        manifest = ji.getManifest();
      } else if (unpack) {
        // Is 1000000 enough, Must be big enough to hold the MANIFEST.MF entry
        // Hope implement of BufferedInputStream allocates dynamicly.
        bis.mark(1000000);
        ji = new JarInputStream(bis, checkSigned);
        manifest = ji.getManifest();
        // If manifest == null then, Manifest probably not first in JAR, should we complain?
        // Now, try to use the jar anyway. Maybe the manifest is there.
        if (manifest == null || !needUnpack(manifest.getMainAttributes())) {
          ji = null;
          bis.reset();
        }
      }
      if (ji != null) {
        file.mkdirs();
        if (manifest == null) {
          if (checkSigned) {
            // TBD? Which exception to use?
            throw new IOException("MANIFEST.MF must be first in archive when using signatures.");
          }
        } else {
          File f = new File(file, META_INF_DIR);
          f.mkdir();
          FileOutputStream fo = new FileOutputStream(new File(f, "MANIFEST.MF"));
          BufferedOutputStream o = new BufferedOutputStream(fo);
          try {
            manifest.write(o);
          } finally {
            o.close();
          }
        }
        boolean verify = checkSigned;
        int verifiedEntries = 0;
        while (processNextJarEntry(ji, verify, file)) {
          if (verify) {
            if (isArchiveSigned()) {
              verifiedEntries++;
            } else {
              verify = false;
            }
          }
        }
        if (verify) {
          checkCertificates(verifiedEntries, true);
        }
        jar = null;
      }
    }
    if (ji == null) {
      if (isDirectory) {
        if (!fileIsReference) {
          sourceFile.copyTo(file);
        }
        if (checkSigned) {
          // NYI! Verify signed directory
        }
        jar = null;
      } else {
        if (!fileIsReference) {
          loadFile(file, bis);
        }
        if (checkSigned) {
          processSignedJar(file);
        }
        jar = new ZipFile(file);
      }
    }
    if (manifest != null) {
      manifest = new AutoManifest(manifest, location);
    } else {
      manifest = getManifest();
    }
    checkManifest();

    handleAutoManifest();

    saveCertificates();
  }


  /**
   * Get the file path from an URL, handling the case where it's
   * a reference:file: URL
   */
  String getFile(URL source) {
    String file = source.getFile();
    if(file.startsWith("file:")) {
      return file.substring(5);
    } else {
      return file;
    }
  }


  boolean isFile(URL source) {
    return source != null && "file".equals(source.getProtocol());
  }


  /**
   * Check if an URL is a reference: URL or if we have global references on all file: URLs
   */
  boolean isReference(URL source) {
    return (source != null) && 
      ("reference".equals(source.getProtocol()) 
       || (fileReference && isFile(source)));
  }

  
  /**
   * Create an Archive based on contents of a saved
   * archive in the specified directory.
   * Take lowest versioned archive and remove rest.
   *
   */
  Archive(File dir, int rev, String location, boolean checkSigned) throws IOException {
    this.location = location;
    String [] f = dir.list();
    file = null;
    if (rev != -1) {
      file = new FileTree(dir, ARCHIVE + rev);
    } else {
      rev = Integer.MAX_VALUE;
      for (int i = 0; i < f.length; i++) {
        if (f[i].startsWith(ARCHIVE)) {
          try {
            int c = Integer.parseInt(f[i].substring(ARCHIVE.length()));
            if (c < rev) {
              rev = c;
              file = new FileTree(dir, f[i]);
            }
          } catch (NumberFormatException ignore) { }
        }
      }
    }
    for (int i = 0; i < f.length; i++) {
      if (f[i].startsWith(ARCHIVE)) {
        try {
          int c = Integer.parseInt(f[i].substring(ARCHIVE.length()));
          if (c != rev) {
            (new FileTree(dir, f[i])).delete();
          }
        } catch (NumberFormatException ignore) { }
      }
      if (f[i].startsWith(SUBDIR)) {
        try {
          int c = Integer.parseInt(f[i].substring(SUBDIR.length()));
          if (c != rev) {
            (new FileTree(dir, f[i])).delete();
          }
        } catch (NumberFormatException ignore) { }
      }
    }
    if (file == null) {
      if (location != null) {
        try {
          URL url = new URL(location);
          file = new FileTree(getFile(url));
        } catch (Exception e) {
          throw new IOException("Bad file URL stored in referenced jar in: " +
                                dir.getAbsolutePath() +
                                ", location=" + location);
        }
      }
      if (file == null || !file.exists()) {
        throw new IOException("No saved jar file found in: " + dir.getAbsolutePath() + ", old location=" + location);
      }
    }

    if (file.isDirectory()) {
      jar = null;
    } else {
      jar = new ZipFile(file);
    }
    if (checkSigned) {
      loadCertificates();
    }
    if (manifest == null) {
      manifest = getManifest();
    }
    handleAutoManifest();
  }


  /**
   * Create a Sub-Archive based on a path to in an already
   * existing Archive. The new archive is saved in a subdirectory
   * below local copy of the existing Archive.
   *
   * @param a Parent Archive.
   * @param path Path of new Archive inside old Archive.
   * @exception FileNotFoundException if no such Jar file in archive.
   * @exception IOException if failed to read Jar file.
   */
  Archive(Archive a, String path) throws IOException {
    this.location = a.location;
    if (a.jar != null) {
      jar = a.jar;
      // Try a directory first, make sure that path ends with "/"
      if (!path.endsWith("/")) {
        path += "/";
      }
      subJar = jar.getEntry(path);
      if (subJar == null) {
        subJar = jar.getEntry(path.substring(0,path.length()-1));
      }
      if (subJar == null) {
        throw new IOException("No such JAR component: " + path);
      }
      file = a.file;
    } else {
      file = findFile(a.file, path);
      if (file.isDirectory()) {
        jar = null;
      } else {
        jar = new ZipFile(file);
      }
    }
  }


  /**
   * Show file name for archive, if zip show if it is sub archive.
   *
   * @return A string with result.
   */
  public String toString() {
    if (subJar != null) {
      return file.getAbsolutePath() + "(" + subJar.getName() + ")";
    } else {
      return file.getAbsolutePath();
    }
  }


  /**
   * Get revision number this archive.
   *
   * @return Archive revision number
   */
  int getRevision() {
    try {
      return Integer.parseInt(file.getName().substring(ARCHIVE.length()));
    } catch (NumberFormatException ignore) {
      // assert?
      return -1;
    }
  }


  /**
   * Get an attribute from the manifest of the archive.
   *
   * @param key Name of attribute to get.
   * @return A string with result or null if the entry doesn't exists.
   */
  String getAttribute(String key) {
    Attributes a = manifest.getMainAttributes();
    if (a != null) {
      return a.getValue(key);
    }
    return null;
  }


  /**
   * Get a byte array containg the contents of named class file from
   * the archive.
   *
   * @param Class File to get.
   * @return Byte array with contents of class file or null if file doesn't exist.
   * @exception IOException if failed to read jar entry.
   */
  byte[] getClassBytes(String classFile) throws IOException {
    if(bClosed) {
      return null;
    }
    InputFlow cif = getInputFlow(classFile);
    if (cif != null) {
      byte[] bytes;
      if (cif.length >= 0) {
        bytes = new byte[(int)cif.length];
        DataInputStream dis = new DataInputStream(cif.is);
        dis.readFully(bytes);
      } else {
        bytes = new byte[0];
        byte[] tmp = new byte[8192];
        try {
          int len;
          while ((len = cif.is.read(tmp)) > 0) {
            byte[] oldbytes = bytes;
            bytes = new byte[oldbytes.length + len];
            System.arraycopy(oldbytes, 0, bytes, 0, oldbytes.length);
            System.arraycopy(tmp, 0, bytes, oldbytes.length, len);
          }
        }
        catch (EOFException ignore) {
          // On Pjava we somtimes get a mysterious EOF excpetion,
          // but everything seems okey. (SUN Bug 4040920)
        }
      }
      cif.is.close();
      return bytes;
    } else {
      return null;
    }
  }


  /**
   * Get an InputFlow to named entry inside an Archive.
   *
   * @param component Entry to get reference to.
   * @return InputFlow to entry or null if it doesn't exist.
   */
  InputFlow getInputFlow(String component) {
    if(bClosed) {
      return null;
    }
    if (component.startsWith("/")) {
      throw new RuntimeException("Assert! Path should never start with / here");
    }
    ZipEntry ze;
    try {
      if (jar != null) {
        if (subJar != null) {
          if (subJar.isDirectory()) {
            ze = jar.getEntry(subJar.getName() + component);
          } else {
            if (component.equals("")) {
              // Return a stream to the entire Jar.
              return new InputFlow(jar.getInputStream(subJar), subJar.getSize());
            } else {
              JarInputStream ji = new JarInputStream(jar.getInputStream(subJar));
              do {
                ze = ji.getNextJarEntry();
                if (ze == null) {
                  ji.close();
                  return null;
                }
              } while (!component.equals(ze.getName()));
              return new InputFlow((InputStream)ji, ze.getSize());
            }
          }
        } else {
          if (component.equals("")) {
            // Return a stream to the entire Jar.
            File f = new File(jar.getName());
            return new InputFlow(new FileInputStream(f), f.length() );
          } else {
            ze = jar.getEntry(component);
          }
        }
        return ze != null ? new InputFlow(jar.getInputStream(ze), ze.getSize()) : null;
      } else {
        File f = findFile(file, component);
        return f.exists() ? new InputFlow(new FileInputStream(f), f.length()) : null;
      }
    } catch (IOException ignore) {
      return null;
    }
  }


  //TODO not extensively tested
  Enumeration findResourcesPath(String path) {
    Vector answer = new Vector();
    if (jar != null) {
      ZipEntry entry;
      //"normalize" + erroneous path check: be generous
      path.replace('\\', '/');
      if (path.startsWith("/")){
        path =  path.substring(1);
      }
      if (!path.endsWith("/")/*in case bad argument*/){
        if (path.length() > 1){
          path += "/";
        }
      }

      Enumeration entries = jar.entries();
      while (entries.hasMoreElements()){
        entry = (ZipEntry) entries.nextElement();
        String name = entry.getName();
        if (name.startsWith(path)){
          int idx = name.lastIndexOf('/');
          if (entry.isDirectory()){
            idx = name.substring(0, idx).lastIndexOf('/');
          }
          if (idx > 0){
            if (name.substring(0, idx + 1).equals(path)){
              answer.add(name);
            }
          } else if (path.equals("")){
            answer.add(name);
          }
        }
      }
    } else {
      File f = findFile(file, path);
      if (!f.exists()) {
        return null;
      }
      if (!f.isDirectory()) {
        return null;
      }
      File[] files = f.listFiles();
      int length = files.length;
      for(int i = 0; i < length; i++){
        String filePath = files[i].getPath();
        filePath = filePath.substring(file.getPath().length() + 1);
        filePath = filePath.replace(File.separatorChar, '/');
        if(files[i].isDirectory()){
          filePath += "/";
        }
        answer.add(filePath);
      }
    }

    if(answer.size() == 0){
      return null;
    }
    return answer.elements();
  }


  /**
   * Get an Archive handle to a named Jar file within this archive.
   *
   * @param path Name of Jar file to get.
   * @return An Archive object representing new archive.
   * @exception FileNotFoundException if no such Jar file in archive.
   * @exception IOException if failed to read Jar file.
   */
  Archive getSubArchive(String path) throws IOException {
    if(bClosed) {
      return null;
    }
    return new Archive(this, path);
  }


  /**
   * Extract native library from JAR.
   *
   * @param key Name of Jar file to get.
   * @return A string with path to native library.
   */
  String getNativeLibrary(String path) throws IOException {
    if(bClosed) {
      throw new IOException("Archive is closed");
    }
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    File lib;
    if (jar != null) {
      lib = getSubFile(this, path);
      if (!lib.exists()) {
        (new File(lib.getParent())).mkdirs();
        ZipEntry ze = jar.getEntry(path);
        if (ze != null) {
          InputStream is = jar.getInputStream(ze);
          try {
            loadFile(lib, is);
          } finally {
            is.close();
          }
        } else {
          throw new FileNotFoundException("No such sub-archive: " + path);
        }
      }
    } else {
      lib = findFile(file, path);
      //XXX - start L-3 modification
      if (!lib.exists() && (lib.getParent() != null)) {
        final String libname = lib.getName();
        File[] list = lib.getParentFile().listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
              int pos = name.lastIndexOf(libname);
              return ((pos > 1) && (name.charAt(pos - 1) == '_'));
            }
          });
        if (list.length > 0) {
          list[0].renameTo(lib);
        }
      }
      //XXX - end L-3 modification
    }
    return lib.getAbsolutePath();
  }


  /**
   * Remove archive and any unpacked sub-archives.
   */
  void purge() {
    close();
    // Remove archive if not  flagged as keep
    if(!fileIsReference) {
      file.delete();
    }

    // Remove any cached sub files
    getSubFileTree(this).delete();

    // Remove any saved certificates.
    removeCertificates();
  }


  /**
   * Close archive and all open sub-archives.
   * If close fails it is silently ignored.
   */
  void close() {
    bClosed = true; // Mark as closed to safely handle referenced files
    if (subJar == null && jar != null) {
      try {
        jar.close();
      } catch (IOException ignore) {}
    }
  }


  /**
   * Returns the path to this bundle.
   */
  String getPath() {
    return file.getAbsolutePath();
  }


  /**
   * Return certificates for signed bundle, otherwise null.
   *
   * @return An array of certificates or null.
   */
  Certificate [] getCertificates() {
    return certs;
  }


  /**
   * Invalidate certificates associated with with bundle archive.
   *
   */
  void invalidateCertificates() {
    certs = null;
  }

  //
  // Private methods
  //

  /**
   * Check that we have a valid manifest.
   *
   * @exception IllegalArgumentException if we have a broken manifest.
   */
  private void checkManifest() {
    Attributes a = manifest.getMainAttributes();
    Util.parseEntries(Constants.EXPORT_PACKAGE, a.getValue(Constants.EXPORT_PACKAGE),
                      false, true, false);
    Util.parseEntries(Constants.IMPORT_PACKAGE, a.getValue(Constants.IMPORT_PACKAGE),
                      false, true, false);
    // NYI, more checks?
  }


  /**
   * Check if we need to unpack bundle,
   * i.e has native code file or subjars.
   *
   * @return true if bundle needs to be unpacked.
   * @exception IllegalArgumentException if we have a broken manifest.
   */
  private boolean needUnpack(Attributes a) {
    Iterator nc = Util.parseEntries(Constants.BUNDLE_NATIVECODE,
                                    a.getValue(Constants.BUNDLE_NATIVECODE),
                                    false, false, false);
    String bc = a.getValue(Constants.BUNDLE_CLASSPATH);
    return (bc != null && !bc.trim().equals(".")) || nc.hasNext();
  }


  /**
   * Handle automanifest stuff.
   * Archive manifest must be set.
   */
  private void handleAutoManifest() throws IOException {
    // TBD Should we check this, should it not always be true!
    if (manifest instanceof AutoManifest) {
      AutoManifest mf = (AutoManifest)manifest;
      if (mf.isAuto()) {
        if (jar != null) {
          mf.addZipFile(jar);
        } else if (file != null && file.isDirectory()) {
          mf.addFile(file.getAbsolutePath(), file);
        }
      }
    }
  }


  /**
   * Get file handle for file inside a directory structure.
   * The path for the file is always specified with a '/'
   * separated path.
   *
   * @param root Directory structure to search.
   * @param path Path to file to find.
   * @return The File object for file <code>path</code>.
   */
  private FileTree findFile(File root, String path) {
    return new FileTree(root, path.replace('/', File.separatorChar));
  }


  /**
   * Get the manifest for this archive.
   *
   * @return The manifest for this Archive
   */
  private AutoManifest getManifest() throws IOException {
    // TBD: Should recognize entry with lower case?
    InputFlow mif = getInputFlow("META-INF/MANIFEST.MF");
    if (mif != null) {
      return new AutoManifest(new Manifest(mif.is), location);
    } else {
      throw new IOException("Manifest is missing");
    }
  }


  /**
   * Get dir for unpacked components.
   *
   * @param archive Archive which contains the components.
   * @return FileTree for archives component cache directory.
   */
  private FileTree getSubFileTree(Archive archive) {
    return new FileTree(archive.file.getParent(),
                        SUBDIR + archive.file.getName().substring(ARCHIVE.length()));
  }


  /**
   * Get file for an unpacked component.
   *
   * @param archive Archive which contains the component.
   * @param path Name of the component to get.
   * @return File for components cache file.
   */
  private File getSubFile(Archive archive, String path) {
    return new File(getSubFileTree(archive), path.replace('/', '-'));
  }


  /**
   * Loads a file from an InputStream and stores it in a file.
   *
   * @param output File to save data in, if <code>null</code>, discard output.
   * @param is InputStream to read from.
   */
  private void loadFile(File output, InputStream is) throws IOException {
    OutputStream os = null;
    try {
      if (output != null) {
        os = new FileOutputStream(output);
      }
      byte[] buf = new byte[8192];
      int n;
      try {
        while ((n = is.read(buf)) >= 0) {
          if (os != null)  {
            os.write(buf, 0, n);
          }
        }
      } catch (EOFException ignore) {
        // On Pjava we sometimes get a mysterious EOF exception,
        // but everything seems okey. (SUN Bug 4040920)
      }
    } catch (IOException e) {
      if (os != null) {
        output.delete();
      }
      throw e;
    } finally {
      if (os != null) {
        os.close();
      }
    }
  }


  /**
   * Check if archive is signed
   */
  private boolean isArchiveSigned() {
    return certs != null;
  }


  /**
   * 
   */
  private boolean processNextJarEntry(JarInputStream ji, boolean verify, File saveDir)
    throws IOException {
    JarEntry je;
    while ((je = ji.getNextJarEntry()) != null) {
      if (je.isDirectory()) {
        continue;
      }
      String name = je.getName();
      if (saveDir != null && (!name.startsWith(OSGI_OPT_DIR) ||
                              name.equals(LOCAL_PERMISSION_FILE))) {
        StringTokenizer st = new StringTokenizer(name, "/");
        File f = new File(saveDir, st.nextToken());
        while (st.hasMoreTokens()) {
          f.mkdir();
          f = new File(f, st.nextToken());
        }
        loadFile(f, ji);
      } else {
        // Read entry to update certificates.
        loadFile(null, ji);
      }
      ji.closeEntry();
      if (name.startsWith(META_INF_DIR)) {
        continue;
      }
      if (verify) {
        Certificate [] c = je.getCertificates();
        if (c != null) {
          if (certs != null) {
            // TBD, perhaps we should allow permuted chains.
            if (!Arrays.equals(c, certs)) {
              certs = null;
            }
          } else {
            certs = c;
          }
        } else {
          certs = null;
        }
      }
      return true;
    }
    return false;
  }


  /**
   * Go through jar file and check signatures.
   */
  private void processSignedJar(File file)
    throws IOException, BundleException
  {
    FileInputStream fis = new FileInputStream(file);
    try {
      BufferedInputStream bis = new BufferedInputStream(fis);
      JarInputStream ji = new JarInputStream(bis);
      String entry;
      int count = 0;

      manifest = ji.getManifest();
    
      while (processNextJarEntry(ji, true, null)) {
        if (isArchiveSigned()) {
          count++;
        } else {
          break;
        }
      }
      checkCertificates(count, true);
    } finally {
      fis.close();
    }
  }


  /**
   * Check that all entries in the bundle is signed.
   * 
   */
  private void checkCertificates(int filesVerified, boolean complete)
    throws BundleException {
    // TBD! Does manifest.getEntries contain more than signers?
    if (filesVerified > 0) {
      int mentries;
      if (complete) {
        mentries = manifest.getEntries().size();
      } else {
        mentries = 0;
        for (Iterator i = manifest.getEntries().keySet().iterator(); i.hasNext();) {
          String name = (String)i.next();
          if (!name.startsWith(OSGI_OPT_DIR) || name.equals(LOCAL_PERMISSION_FILE)) {
            mentries++;
          }
        }
      }
      if (mentries != filesVerified) {
        certs = null;
        System.err.println("All entries in bundle not completly signed (" +
                           mentries + " != " + filesVerified + ")");
        // NYI! Log this
      }
    }
  }


  /**
   * 
   */
  public void saveCertificates() throws IOException {
    File f = new File(getPath() + CERTS_SUFFIX);
    if (certs != null) {
      try {
        FileOutputStream fos = new FileOutputStream(f);
        for (int i = 0; i < certs.length; i++) {
          fos.write(certs[i].getEncoded());
        }
        fos.close();
      } catch (CertificateEncodingException e) {
        // NYI! Log or fail
      }
    }
  }


  /**
   * TBD improve this.
   */
  private void loadCertificates() throws IOException {
    File f = new File(getPath() + CERTS_SUFFIX);
    if (f.canRead()) {
      try {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        FileInputStream fis = new FileInputStream(f);
        Collection c = cf.generateCertificates(fis);
        // TBD, check if order is preserved
        if (c.size() > 0) {
          certs = new Certificate[c.size()];
          certs = (Certificate [])c.toArray(certs);
        }
      } catch (CertificateException ioe) {
        // NYI! Log or fail
      }
    }
    // NYI! load certificates from both trusted and untrusted storage.
  }


  /**
   * 
   */
  public void removeCertificates() {
    File f = new File(getPath() + CERTS_SUFFIX);
    f.delete();
  }



  /**
   * InputFlow represents an InputStream with a known length
   */
  class InputFlow {
    final InputStream is;
    final long length;

    InputFlow(InputStream is, long length) {
      this.is = is;
      this.length = length;
    }
  }

}
