/*
 * Copyright (c) 2004, KNOPFLERFISH project
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

package org.knopflerfish.bundle.junit;

import java.util.*;
import java.net.URL;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.osgi.framework.*;
import org.osgi.service.http.*;
import org.knopflerfish.service.log.LogRef;
import junit.framework.*;

public class JUnitServlet extends HttpServlet {

  static final String ID     = "id";
  static final String SUBID  = "subid";
  static final String CMD    = "cmd";
  static final String FMT    = "fmt";

  JUnitServlet() {
  }

  public void doGet(HttpServletRequest  request, 
		     HttpServletResponse response) 
    throws ServletException,
	   IOException 
  {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest  request, 
		     HttpServletResponse response) 
    throws ServletException,
	   IOException 
  {
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

      PrintWriter out = response.getWriter();

      String fmt  = request.getParameter(FMT);
      String cmd  = request.getParameter(CMD);
      String id   = request.getParameter(ID);

      if(id == null && cmd == null) {
	cmd = "list";
	fmt = "html";
      } else {
	if(cmd == null) {
	  cmd = "run";
	}
      }


      if("html".equals(fmt)) {
	response.setContentType ("text/html");
      } else {
	response.setContentType ("text/xml");
      }


      if("html".equals(fmt)) {
	handleCommandHTML(request, response, cmd, out);
      } else {
	handleCommand(request, response, cmd, out);
      }
      out.flush ();
    } catch (RuntimeException e) {
      Activator.log.error("servlet failed", e);
      throw e;
    } catch (IOException e) {
      Activator.log.error("servlet failed", e);
      throw e;
    } finally {
      Thread.currentThread().setContextClassLoader(oldLoader);
    }
  }

  void handleCommandHTML(HttpServletRequest  request, 
			 HttpServletResponse response,
			 String cmd,
			 PrintWriter out) throws 
			   ServletException,
			   IOException {
    String id    = request.getParameter(ID);
    String subid = request.getParameter(SUBID);
    out.println("<html>");
    out.println("<head>");
    out.print("<title>");
    out.print("JUnit test result from " + id);
    if(subid != null && !"".equals(subid)) {
      out.print("/" + subid);
    }
    out.println("</title>");
    printCSS(out);
    out.println("</head>");
    out.println("<body>");

    try {
      if("run".equals(cmd)) {
	runTestHTML(request, id, out);
      } else if("list".equals(cmd)) {
	showTestsHTML(request, response, out);
      } else {
	throw new IllegalArgumentException("Unknown command='" + cmd + "'");
      }
    } catch (Exception e) {
      out.println("<h3>Failed</h3>");
      out.println("<pre>");
      e.printStackTrace(out);
      out.println("</pre>");
    }
    
    out.println("</body>");
    out.println("</html>");
  }
  
  void printCSS(PrintWriter out) throws IOException {
    InputStream in = null;
    try {
      URL url = getClass().getResource("/style.css");
      in = url.openStream();
      BufferedInputStream bin = new BufferedInputStream(in);
      byte[] buf = new byte[1024];
      int n = 0;
      while(-1 != (n = bin.read(buf, 0, buf.length))) {
	out.print(new String(buf, 0, n));
      }
    } catch (Exception e) {
    } finally {
      try { in.close(); } catch (Exception ignored) { }
    }
  }

  void showTestsHTML(HttpServletRequest  request, 
		     HttpServletResponse response,
		     PrintWriter out) throws Exception
  {

    out.println("<h2>Available tests</h2>");

    String filter =  
      "(|" + 
      "(objectclass=" + Test.class.getName() + ")" + 
      "(objectclass=" + TestSuite.class.getName() + ")" + 
      "(objectclass=" + TestCase.class.getName() + ")" + 
      ")";
    
    ServiceReference[] srl = 
      Activator.bc.getServiceReferences(null, filter);
    
    if(srl == null || srl.length == 0) {
      out.println("No Test services found");
    }

    out.println("<ol>");
    for(int i = 0; srl != null && i < srl.length; i++) {
      Object obj = Activator.bc.getService(srl[i]);
      if(obj instanceof Test) {
	String id   = (String)srl[i].getProperty("service.pid");
	String desc = (String)srl[i].getProperty("service.description");
	
	String link = HttpExporter.SERVLET_ALIAS + 
	  "?" + 
	  CMD + "=run&" + 
	  FMT + "=html&" +
	  ID + "=" + id;
	out.println("<li><a href=\"" + link + "\">" + id + "</a>");
	if(desc != null && !"".equals(desc)) {
	  out.println("<br>" + desc);
	}
	if(obj instanceof TestSuite) {
	  showSubTestsHTML(id, (TestSuite)obj, request, response, out);
	}
      }
      Activator.bc.ungetService(srl[i]);
    }
    out.println("</ol>");
  }

  void showSubTestsHTML(String id, 
			TestSuite suite, 
			HttpServletRequest  request, 
			HttpServletResponse response,
			PrintWriter out) throws Exception
  {
    out.println("<ul>");
    for(int i = 0; i < suite.testCount(); i++) {
      Test t = suite.testAt(i);
      String subid = t.getClass().getName();
      String name  = null;
      if(t instanceof TestCase) {
	name = ((TestCase)t).getName();
      }
      if(t instanceof TestSuite) {
	name = ((TestSuite)t).getName();
      }
      if(name == null) {
	name = subid;
      }

      String link = HttpExporter.SERVLET_ALIAS + 
	"?" + 
	CMD + "=run&" + 
	FMT + "=html&" +
	ID + "=" + id + "&" + 
	SUBID + "=" + subid;
      out.println("<li><a href=\"" + link + "\">" + name + "</a>");
      if(t instanceof TestSuite) {
	showSubTestsHTML(id, (TestSuite)t, request, response, out);
      }
    }
    out.println("</ul>");
  }

  void handleCommand(HttpServletRequest  request, 
		     HttpServletResponse response,
		     String cmd,
		     PrintWriter out) throws 
		       ServletException,
		       IOException {
    out.println("<?xml version=\"1.0\"?>");
    out.println("<junit>");
    try {
      if("run".equals(cmd)) {
	runTest(request, request.getParameter(ID), out);
      } else {
	throw new IllegalArgumentException("Unknown command='" + cmd + "'");
      }
    } catch (Exception e) {
      out.println(" <fail " + 
		  "  message=\"" + e.getMessage() + "\"" + 
		  "  exception=\"" + e.getClass().getName() + "\">");
      dumpException(e, out);
      out.println(" </fail>");
    }

    out.println("</junit>");
  }

  void runTestHTML(HttpServletRequest request, 
		   String id, 
		   PrintWriter out) throws Exception {
    String subid = request.getParameter(SUBID);
    
    out.println("<h2>Test result of " + id);
    if(subid != null) {
      out.println(" - " + subid);
    }
    out.println("</h2>");

    
    out.println("<p><a href=\"" + HttpExporter.SERVLET_ALIAS + "\">List all available tests</a></p>");

    out.println("<p><a href=\"" + HttpExporter.SERVLET_ALIAS + 
		"?" + ID + "=" + id + "&" + FMT + "=xml\"" +
		">Run again and show as XML</a></p>");
    
    TestSuite suite = getSuite(request);
    
    TestResult tr = new TestResult();
      
    suite.run(tr);
    
    dumpResultHTML(tr, out);
  }
  
  void runTest(HttpServletRequest request, 
	       String id, 
	       PrintWriter out) throws Exception {
    out.println(" <testcase id=\"" + id + "\">");

    try {

      TestSuite suite = getSuite(request);
      
      dumpSuite(suite, out, 2);
      
      TestResult tr = new TestResult();
      
      System.out.println("run test on " + suite);
      suite.run(tr);
    
      dumpResult(tr, out);
    } finally {
      out.println(" </testcase>");
    }
  }

  TestSuite getSuite(HttpServletRequest request) throws Exception {
    Object obj = null;

    final String id    = request.getParameter(ID);
    final String subid = request.getParameter(SUBID);
    
    ServiceReference[] srl = 
      Activator.bc.getServiceReferences(null, "(service.pid=" + id + ")");
    
    if(srl == null || srl.length == 0) {
      obj = new TestCase("No id=" + id) {
	  public void runTest() {
	    throw new IllegalArgumentException("No test with id=" + id);
	  }
	};
    }
    if(srl != null && srl.length != 1) {
      obj = new TestCase("Multiple id=" + id) {
	  public void runTest() {
	    throw new IllegalArgumentException("More than one test with id=" + id);
	  }
	};
    }
    
    if(obj == null) {
      obj = Activator.bc.getService(srl[0]);
    }
    
    if(!(obj instanceof Test)) {
      final Object oldObj = obj;
      obj = new TestCase("ClassCastException") {
	  public void runTest() {
	    throw new ClassCastException("Service implements " + 
					 oldObj.getClass().getName() + 
					 " instead of " + 
					 Test.class.getName());
	  }
	};
    }
    
    Test test = (Test)obj;
    
    TestSuite suite;

    if(subid != null && !"".equals(subid)) {
      Test subtest = findTest(test, subid);      
      if(subtest != null) {
	test = subtest;
      } else {
	test = new TestCase("IllegalArumentException") {
	    public void runTest() {
	      throw new ClassCastException("subtest " + subid + " not found");
	    }
	  };
      }
    }

    if(test instanceof TestSuite) {
      suite = (TestSuite)test;
    } else {
      suite = new TestSuite();
      suite.addTest(test);
    }

    
    return suite;
  }
  
  Test findTest(Test test, String id) {
    if(test instanceof TestSuite) {
      TestSuite ts = (TestSuite)test;
      if(id.equals(ts.getName()) || id.equals(ts.getClass().getName())) {
	return ts;
      }
      for(int i = 0; i < ts.testCount(); i++) {
	Test child = ts.testAt(i);
	Test r = findTest(child, id);
	if(r != null) {
	  return r;
	}
      }
    }
    if(test instanceof TestCase) {
      TestCase tc = (TestCase)test;
      if(id.equals(tc.getName())) {
	return tc;
      }
    }
    if(id.equals(test.getClass().getName())) {
      return test;
    }
    return null;
  }

  static String indent(int n) {
    StringBuffer sb = new StringBuffer();
    while(n --> 0) {
      sb.append(" ");
    }
    return sb.toString();
  }

  void dumpSuite(TestSuite suite, 
		 PrintWriter out, 
		 int n) throws IOException {
    out.print(indent(n) + "  <suite class = \"" + 
		suite.getClass().getName() + "\"");
    out.println(" name  = \"" + suite.getName() + "\">");

    for(int i = 0; i < suite.testCount(); i++) {
      Test test = suite.testAt(i);
      String clazz = test.getClass().getName();
      String name  = null;
      if(test instanceof TestCase) {
	name = ((TestCase)test).getName();
      }
      if(name == null) {
	name = clazz;
      }
      if(test instanceof TestSuite) {
	dumpSuite((TestSuite)test, out, n + 1);
      } else {
	out.print(indent(n) + "   <case class = \"" + clazz + "\"");
	out.println(" name  = \"" + name  + "\"/>");
      }
    }
    out.println(indent(n) + "  </suite>");
  }

  void dumpResultHTML(TestResult tr, PrintWriter out) throws IOException {
    if(tr.wasSuccessful()) {
      out.println("<div class=\"testok\">");
      out.println("All tests in suite OK");
      out.println("</div>");
    } else {
      out.println("<div class=\"testfailed\">");
      out.println("Some tests in suite failed");
      out.println("</div>");
    }

    out.println("<pre>");
    out.println("# of tests:    " + tr.runCount());
    out.println("# of failures: " + tr.failureCount());
    out.println("# of errors:   " + tr.errorCount());
    out.println("</pre>");

    if(tr.failureCount() > 0 ) {
      int i = 0;
      for(Enumeration e = tr.failures(); e.hasMoreElements(); ) {
	i++;
	TestFailure tf = (TestFailure)e.nextElement();
	dumpFailureHTML(tf, out, "Failure #" + i);
      }
    }
    if(tr.errorCount() > 0 ) {
      int i = 0;
      for(Enumeration e = tr.errors(); e.hasMoreElements(); ) {
	i++;
	TestFailure tf = (TestFailure)e.nextElement();
	dumpFailureHTML(tf, out, "Error #" + i);
      }
    }
  }


  void dumpResult(TestResult tr, PrintWriter out) throws IOException {
    out.println("  <testresult wasSuccessful = \"" + tr.wasSuccessful() + "\"");
    out.println("              runCount      = \"" + tr.runCount() + "\"");
    out.println("              failureCount  = \"" + tr.failureCount() + "\"");
    out.println("              errorCount    = \"" + tr.errorCount() + "\"");
    out.println("  >");
    if(tr.failureCount() > 0 ) {
      out.println("   <failures>");
      for(Enumeration e = tr.failures(); e.hasMoreElements(); ) {
	TestFailure tf = (TestFailure)e.nextElement();
	dumpFailure(tf, out);
      }
      out.println("   </failures>");
    }
    if(tr.errorCount() > 0 ) {
      out.println("   <errors>");
      for(Enumeration e = tr.errors(); e.hasMoreElements(); ) {
	
	TestFailure tf = (TestFailure)e.nextElement();
	dumpFailure(tf, out);
      }
      out.println("   </errors>");
    }
    out.println("  </testresult>");
  }

  void dumpFailure(TestFailure tf, PrintWriter out) throws IOException {
    out.println("    <failure");

    out.println("      exceptionMessage=\"" + escape(tf.exceptionMessage()) + "\"");
    Test failedTest = tf.failedTest();
    if(failedTest instanceof TestCase) {
      TestCase tc = (TestCase)failedTest;
      String name = tc.getName();
      if(name == null) {
	name = tc.getClass().getName();
      }

      out.println("      failedTestCaseName=\"" + escape(name) + "\"");
      out.println("      failedTestCaseClass=\"" + tc.getClass().getName() + "\"");
    } else {
      out.println("      failedTest=\"" + tf.failedTest() + "\"");
    }
    out.println("    >");
    out.print("     <trace><![CDATA[");
    out.print(tf.trace());
    out.println("]]></trace>");
    
    out.println("    </failure>");
  }

  void dumpFailureHTML(TestFailure tf, PrintWriter out,
		       String prefix) throws IOException {
    out.println("<div class=\"schemaComp\">");
    out.println(" <div class=\"compHeader\">");
    out.println("  <span class=\"schemaComp\">" + 
		prefix);
    if(null != tf.exceptionMessage()) {
      out.println(" - " + tf.exceptionMessage());
    }
    out.println("  </span>");
    out.println(" </div>");
    out.println("<div class=\"compBody\">");

    Test failedTest = tf.failedTest();

    out.println("<p>");
    if(failedTest instanceof TestCase) {
      TestCase tc = (TestCase)failedTest;
      String name = tc.getName();
      if(name == null) {
	name = tc.getClass().getName();
      }

      out.println("Name:  <code>" + escape(name) + "</code><br>");
      out.println("Class: <code>" + tc.getClass().getName() + "</code>");
    } else {
      out.println("<code>" + tf.failedTest() + "</code>");
    }
    out.println("</p>");

    out.println("<pre>");
    out.println("");
    out.print(tf.trace());
    out.println("</pre>");

    out.println("</div>");
    out.println("</div>");

  }

  String escape(String s) {
    if(s == null) {
      return null;
    }
    return 
      s
      .replace('<', '[').replace('>', ']')
      .replace('\"', '\'');
  }

  void dumpError(AssertionFailedError af, PrintWriter out) throws IOException {
    out.println("    <error ");
    out.println("      exceptionMessage=\"" + af.getMessage() + "\"");
    out.println("    >");
    dumpException(af, out);
    out.println("    </failure>");
  }

  void dumpException(Throwable t, PrintWriter out) throws IOException {
    out.print("     <exception class=\"" + t.getClass().getName() + "\">");
    out.print("<![CDATA[");
    t.printStackTrace(out);
    out.println("]]></exception>");
  }
}
