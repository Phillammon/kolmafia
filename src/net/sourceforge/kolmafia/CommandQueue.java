package net.sourceforge.kolmafia;

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.sourceforge.kolmafia.utilities.PauseObject;
import net.sourceforge.kolmafia.utilities.StringUtilities;

public class CommandQueue {
  private static final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();
  private static final CommandQueueHandler handler = new CommandQueueHandler();

  static {
    CommandQueue.handler.start();
  }

  public static boolean hasQueuedCommands() {
    return !CommandQueue.commandQueue.isEmpty() || handler.command != null;
  }

  public static void executeCommand(final String command) {
    if (command.isEmpty()) {
      return;
    }

    if (command.equals("abort") || command.equals("--")) {
      RequestThread.declareWorldPeace();
      return;
    }

    if (command.startsWith("jstack")
        || command.startsWith("graygui")
        || command.startsWith("greygui")
        || command.equalsIgnoreCase("clear")
        || command.equalsIgnoreCase("cls")
        || command.equalsIgnoreCase("reset")) {
      KoLmafiaCLI.DEFAULT_SHELL.executeLine(command);
      return;
    }

    if (CommandQueue.hasQueuedCommands() || KoLmafia.isAdventuring()) {
      RequestLogger.printLine();

      if (!KoLmafia.isAdventuring()) {
        RequestLogger.printHtml(
            " &gt; <b>CURRENT</b>"
                + StringUtilities.getEntityEncode(": " + handler.command, false));
      }

      Iterator<String> commandIterator = CommandQueue.commandQueue.iterator();

      int i;
      for (i = 1; commandIterator.hasNext(); ++i) {
        RequestLogger.printHtml(
            " &gt; <b>QUEUED "
                + i
                + "</b>"
                + StringUtilities.getEntityEncode(": " + commandIterator.next(), false));
      }

      RequestLogger.printHtml(
          " &gt; <b>QUEUED " + i + "</b>: " + StringUtilities.getEntityEncode(command, false));
      RequestLogger.printLine();
    }

    CommandQueue.commandQueue.add(command);
  }

  private static final class CommandQueueHandler extends Thread {
    private String command = null;
    private final PauseObject pauser = new PauseObject();

    public CommandQueueHandler() {
      super("CommandQueueHandler");
    }

    @Override
    public void run() {
      while (true) {
        try {
          this.command = CommandQueue.commandQueue.take();
        } catch (InterruptedException e) {
          StaticEntity.printStackTrace(e);
          continue;
        }

        Integer requestId = RequestThread.openRequestSequence();
        try {
          this.handleQueue();
        } catch (Exception e) {
          StaticEntity.printStackTrace(e);
        } finally {
          RequestThread.closeRequestSequence(requestId);
        }
      }
    }

    public void handleQueue() {
      do {
        // Don't try running commands whilst running adventures, it causes unexpected results
        while (!KoLmafia.refusesContinue() && KoLmafia.isAdventuring()) {
          this.pauser.pause(500);
        }

        RequestLogger.printLine();
        RequestLogger.printLine(" &gt; " + StringUtilities.getEntityEncode(this.command, false));
        RequestLogger.printLine();

        try {
          KoLmafia.forceContinue();
          KoLmafiaCLI.DEFAULT_SHELL.executeLine(this.command);
        } catch (Exception e) {
          StaticEntity.printStackTrace(e);
        }

        if (KoLmafia.refusesContinue()) {
          CommandQueue.commandQueue.clear();
        }

        this.command = CommandQueue.commandQueue.poll();

      } while (this.command != null);
    }
  }
}
