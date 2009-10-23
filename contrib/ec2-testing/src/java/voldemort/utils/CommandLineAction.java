/*
 * Copyright 2009 LinkedIn, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class CommandLineAction {

    protected final Log logger = LogFactory.getLog(getClass());

    protected void run(String commandId,
                       Collection<String> hostNames,
                       String hostUserId,
                       File sshPrivateKey,
                       String voldemortRootDirectory,
                       String voldemortHomeDirectory,
                       File sourceDirectory,
                       long timeout,
                       StringBuilder errors) throws IOException {
        ExecutorService threadPool = Executors.newFixedThreadPool(hostNames.size());
        List<Future<Object>> futures = new ArrayList<Future<Object>>();

        Properties properties = new Properties();
        properties.load(getClass().getClassLoader().getResourceAsStream("commands.properties"));

        final String rawCommand = properties.getProperty(commandId);

        for(String hostName: hostNames) {
            String parameterizedCommand = parameterizeCommand(hostName,
                                                              hostUserId,
                                                              sshPrivateKey,
                                                              voldemortRootDirectory,
                                                              voldemortHomeDirectory,
                                                              sourceDirectory,
                                                              rawCommand);
            List<String> commandArgs = generateCommandArgs(parameterizedCommand);
            UnixCommand command = new UnixCommand(hostName, commandArgs);

            CommandOutputListener commandOutputListener = new LoggingCommandOutputListener();
            Future<Object> future = threadPool.submit(new ExitCodeCallable(command,
                                                                           commandOutputListener));
            futures.add(future);
        }

        for(Future<Object> future: futures) {
            Throwable t = null;

            try {
                future.get(timeout, TimeUnit.MILLISECONDS);
            } catch(ExecutionException ex) {
                t = ex.getCause();
            } catch(Exception e) {
                t = e;
            }

            if(t != null) {
                if(logger.isWarnEnabled())
                    logger.warn(t, t);

                if(errors.length() > 0)
                    errors.append("; ");

                errors.append(t.getMessage());
            }
        }

        threadPool.shutdown();

        try {
            threadPool.awaitTermination(60, TimeUnit.SECONDS);
        } catch(InterruptedException e) {
            if(logger.isWarnEnabled())
                logger.warn(e, e);
        }
    }

    private String parameterizeCommand(String hostName,
                                       String hostUserId,
                                       File sshPrivateKey,
                                       String voldemortRootDirectory,
                                       String voldemortHomeDirectory,
                                       File sourceDirectory,
                                       String command) {
        Map<String, String> variableMap = new HashMap<String, String>();
        variableMap.put("hostName", hostName);
        variableMap.put("hostUserId", hostUserId);

        if(sshPrivateKey != null)
            variableMap.put("sshPrivateKey", sshPrivateKey.getAbsolutePath());

        variableMap.put("voldemortRootDirectory", voldemortRootDirectory);
        variableMap.put("voldemortHomeDirectory", voldemortHomeDirectory);

        if(sourceDirectory != null)
            variableMap.put("sourceDirectory", sourceDirectory.getAbsolutePath());

        for(Map.Entry<String, String> entry: variableMap.entrySet())
            command = StringUtils.replace(command, "${" + entry.getKey() + "}", entry.getValue());

        return command;
    }

    private List<String> generateCommandArgs(String command) {
        List<String> commands = new ArrayList<String>();
        boolean isInQuotes = false;
        int start = 0;

        for(int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if(c == '\"') {
                isInQuotes = !isInQuotes;
            } else if(c == ' ' && !isInQuotes) {
                String substring = command.substring(start, i).trim();
                start = i + 1;

                if(substring.trim().length() > 0)
                    commands.add(substring.replace("\"", ""));
            }
        }

        String substring = command.substring(start).trim();

        if(substring.length() > 0)
            commands.add(substring.replace("\"", ""));

        if(logger.isDebugEnabled())
            logger.debug("Command to execute: " + commands.toString());

        return commands;
    }

    protected abstract class DelegatingCommandOutputListener implements CommandOutputListener {

        protected final CommandOutputListener delegate;

        public DelegatingCommandOutputListener(CommandOutputListener delegate) {
            this.delegate = delegate;
        }

    }

    protected class LoggingCommandOutputListener extends DelegatingCommandOutputListener {

        public LoggingCommandOutputListener() {
            this(null);
        }

        public LoggingCommandOutputListener(CommandOutputListener delegate) {
            super(delegate);
        }

        public void outputReceived(OutputType outputType, String hostName, String line) {
            if(outputType == OutputType.STDERR) {
                if(logger.isWarnEnabled())
                    logger.warn("ERROR from " + hostName + ": " + line);
            } else if(outputType == OutputType.STDOUT) {
                if(logger.isInfoEnabled())
                    logger.info("From " + hostName + ": " + line);
            }

            if(delegate != null)
                delegate.outputReceived(outputType, hostName, line);
        }
    }

    protected class ExitCodeCallable implements Callable<Object> {

        private final UnixCommand command;

        private final CommandOutputListener commandOutputListener;

        public ExitCodeCallable(UnixCommand command, CommandOutputListener commandOutputListener) {
            this.command = command;
            this.commandOutputListener = commandOutputListener;
        }

        public Object call() throws Exception {
            int exitCode = command.execute(commandOutputListener);

            if(exitCode != 0)
                throw new Exception("Process on " + command.getHostName() + " exited with code "
                                    + exitCode + ". Please check the logs for details.");
            return null;
        }
    }

}
