package org.codeaholics.tools.build.pant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.util.FileUtils;
import org.apache.tools.ant.util.StringUtils;

/**
 * Implementation of a logger which in compatible with a parallel execution of
 * targets.
 *
 * @see ParallelExecutor
 */
public class ParallelExecutorLogger extends DefaultLogger {

    private static final int LEFT_COLUMN_SIZE = 26;

    private static final String STARTED_SYMBOL = "+ ";
    private static final String FINISHED_SYMBOL = "- ";

    public void targetStarted(final BuildEvent event) {
        if (Project.MSG_INFO <= msgOutputLevel && !event.getTarget().getName().equals("")) {
            final String msg = STARTED_SYMBOL + event.getTarget().getName();
            printMessage(msg, out, event.getPriority());
            log(msg);
        }
    }

    public void targetFinished(final BuildEvent event) {
        if (Project.MSG_INFO <= msgOutputLevel && !event.getTarget().getName().equals("")) {
            final String msg = FINISHED_SYMBOL + event.getTarget().getName();
            printMessage(msg, out, event.getPriority());
            log(msg);
        }
    }

    public void messageLogged(final BuildEvent event) {
        final int priority = event.getPriority();
        // Filter out messages based on priority
        if (priority <= msgOutputLevel) {

            final StringBuffer message = new StringBuffer();
            if (event.getTask() != null && !emacsMode) {
                // Print out the name of the (target, task) if we're in one
                final String targetName = event.getTask().getOwningTarget().getName();
                final String taskName = event.getTask().getTaskName();
                String label = "[" + targetName + " / " + taskName + "] ";
                final int size = LEFT_COLUMN_SIZE - label.length();
                final StringBuffer tmp = new StringBuffer();
                for (int i = 0; i < size + 1; i++) {
                    tmp.append(" ");
                }
                tmp.append(label);
                label = tmp.toString();

                BufferedReader r = null;
                try {
                    r = new BufferedReader(new StringReader(event.getMessage()));
                    String line = r.readLine();
                    boolean first = true;
                    do {
                        if (first) {
                            if (line == null) {
                                message.append(label);
                                break;
                            }
                        } else {
                            message.append(StringUtils.LINE_SEP);
                        }
                        first = false;
                        message.append(label).append(line);
                        line = r.readLine();
                    } while (line != null);
                } catch (final IOException e) {
                    // shouldn't be possible
                    message.append(label).append(event.getMessage());
                } finally {
                    if (r != null) {
                        FileUtils.close(r);
                    }
                }

            } else {
                // emacs mode or there is no task
                message.append(event.getMessage());
            }
            final Throwable ex = event.getException();
            if (Project.MSG_DEBUG <= msgOutputLevel && ex != null) {
                message.append(StringUtils.getStackTrace(ex));
            }

            final String msg = message.toString();
            if (priority != Project.MSG_ERR) {
                printMessage(msg, out, priority);
            } else {
                printMessage(msg, err, priority);
            }
            log(msg);
        }
    }

    protected void printMessage(final String message, final PrintStream stream, final int priority) {
        synchronized (this) {
            super.printMessage(message, stream, priority);
        }
    }

}
