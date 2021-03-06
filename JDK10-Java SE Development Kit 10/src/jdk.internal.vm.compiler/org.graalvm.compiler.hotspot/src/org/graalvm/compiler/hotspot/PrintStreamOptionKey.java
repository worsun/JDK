/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package org.graalvm.compiler.hotspot;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;

/**
 * An option that encapsulates and configures a print stream.
 */
public class PrintStreamOptionKey extends OptionKey<String> {

    public PrintStreamOptionKey() {
        super(null);
    }

    /**
     * Replace any instance of %p with an identifying name. Try to get it from the RuntimeMXBean
     * name.
     *
     * @return the name of the file to log to
     */
    private String getFilename(OptionValues options) {
        String name = getValue(options);
        if (name.contains("%p")) {
            try {
                String runtimeName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
                int index = runtimeName.indexOf('@');
                if (index != -1) {
                    long pid = Long.parseLong(runtimeName.substring(0, index));
                    runtimeName = Long.toString(pid);
                }
                name = name.replaceAll("%p", runtimeName);
            } catch (NumberFormatException e) {

            } catch (LinkageError err) {
                name = String.valueOf(org.graalvm.compiler.debug.PathUtilities.getGlobalTimeStamp());
            }
        }
        if (name.contains("%t")) {
            name = name.replaceAll("%t", String.valueOf(System.currentTimeMillis()));
        }
        return name;
    }

    /**
     * An output stream that redirects to {@link HotSpotJVMCIRuntimeProvider#getLogStream()}. The
     * {@link HotSpotJVMCIRuntimeProvider#getLogStream()} value is only accessed the first time an
     * IO operation is performed on the stream. This is required to break a deadlock in early JVMCI
     * initialization.
     */
    static class DelayedOutputStream extends OutputStream {
        private volatile OutputStream lazy;

        private OutputStream lazy() {
            if (lazy == null) {
                synchronized (this) {
                    if (lazy == null) {
                        lazy = HotSpotJVMCIRuntime.runtime().getLogStream();
                    }
                }
            }
            return lazy;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            lazy().write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            lazy().write(b);
        }

        @Override
        public void flush() throws IOException {
            lazy().flush();
        }

        @Override
        public void close() throws IOException {
            lazy().close();
        }
    }

    /**
     * Gets the print stream configured by this option. If no file is configured, the print stream
     * will output to HotSpot's {@link HotSpotJVMCIRuntimeProvider#getLogStream() log} stream.
     */
    public PrintStream getStream(OptionValues options) {
        if (getValue(options) != null) {
            try {
                final boolean enableAutoflush = true;
                PrintStream ps = new PrintStream(new FileOutputStream(getFilename(options)), enableAutoflush);
                /*
                 * Add the JVM and Java arguments to the log file to help identity it.
                 */
                String inputArguments = String.join(" ", java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
                ps.println("VM Arguments: " + inputArguments);
                String cmd = System.getProperty("sun.java.command");
                if (cmd != null) {
                    ps.println("sun.java.command=" + cmd);
                }
                return ps;
            } catch (FileNotFoundException e) {
                throw new RuntimeException("couldn't open file: " + getValue(options), e);
            }
        } else {
            return new PrintStream(new DelayedOutputStream());
        }
    }
}
