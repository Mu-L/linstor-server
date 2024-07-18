package com.linbit.linstor.logging;

import com.linbit.linstor.ErrorContextSupplier;
import com.linbit.linstor.LinStorException;
import com.linbit.linstor.core.LinStor;
import com.linbit.linstor.netcom.Peer;
import com.linbit.linstor.security.AccessContext;
import com.linbit.utils.TimeUtils;

import javax.annotation.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

public abstract class BaseErrorReporter
{
    static final String UNKNOWN_LABEL = "<UNKNOWN>";

    static final String ERROR_FIELD_FORMAT = "%-32s    %s\n";
    static final String SECTION_SEPARATOR;
    static final int SEPARATOR_WIDTH = 60;

    final String dmModule;
    final String nodeName;
    final Calendar cal;

    protected final boolean printStackTraces;


    // Unique instance ID of this error reporter instance
    // The linstor server would typically create one instance of the error reporter upon startup,
    // so that all error reports generated by the same running instance of a linstor server
    // create error reports with the same instance ID.
    // When the linstor server is restarted, the instance ID will change.
    final String instanceId;
    final long instanceEpoch;

    static
    {
        char[] separator = new char[SEPARATOR_WIDTH];
        Arrays.fill(separator, '=');
        SECTION_SEPARATOR = new String(separator);
    }

    public static final Pattern LIGHT_CHECKPOINT_PATTERN =
        Pattern.compile("is identified by light checkpoint \\[([^]]*)");

    @SuppressWarnings("checkstyle:magicnumber")
    BaseErrorReporter(String moduleName, boolean printStackTracesRef, String nodeNameRef)
    {
        dmModule = moduleName;
        nodeName = nodeNameRef;
        HashFunction finger = Hashing.farmHashFingerprint64();
        int fingerprint = finger.hashUnencodedChars(nodeName).asInt();
        // this of course has a fairly chance to colide using only 20bit of the 64bit hash
        // but combined with the timestamp, it still should be very unlikely that a collision will happen
        final int nodeHash = !dmModule.equals(LinStor.CONTROLLER_MODULE) ? fingerprint & 0xFFFFF : 0;
        instanceEpoch = System.currentTimeMillis() / 1000;
        instanceId = String.format("%08X-%05X", instanceEpoch, nodeHash);
        cal = Calendar.getInstance();
        printStackTraces = printStackTracesRef;
    }

    String formatLogMsg(long reportNr, Throwable errorInfo)
    {
        String logMsg;
        if (errorInfo == null)
        {
            logMsg = String.format(
                "Problem logged to report number %s-%06d\n",
                instanceId, reportNr
            );
        }
        else
        {
            String excMsg = errorInfo.getMessage();
            if (excMsg == null)
            {
                logMsg = String.format(
                    "Problem of type '%s' logged to report number %s-%06d\n",
                    errorInfo.getClass().getName(), instanceId, reportNr
                );
            }
            else
            {
                logMsg = excMsg + String.format(
                    " [Report number %s-%06d]\n",
                    instanceId, reportNr
                );
            }
        }
        return logMsg;
    }

    void renderReport(
        ErrorReportRenderer outputRef,
        long reportNr,
        @Nullable AccessContext accCtxRef,
        @Nullable Peer client,
        @Nullable Throwable errorInfo,
        LocalDateTime errorTime,
        @Nullable String contextInfo,
        boolean includeStackTraceRef
    )
    {
        // Error report header
        reportHeader(outputRef, reportNr, accCtxRef, client, errorTime);

        // Generate and report a null pointer exception if this
        // method is called with a null argument
        final Throwable checkedErrorInfo = errorInfo == null ? new NullPointerException() : errorInfo;

        // Report the error and any nested errors
        int loopCtr = 0;
        for (Throwable curErrorInfo = checkedErrorInfo; curErrorInfo != null; curErrorInfo = curErrorInfo.getCause())
        {
            if (loopCtr <= 0)
            {
                if (includeStackTraceRef)
                {
                    outputRef.println("Reported error:\n===============\n");
                }
                else
                {
                    outputRef.println("Reported problem:\n===============\n");
                }
            }
            else
            {
                outputRef.println("Caused by:\n==========\n");
            }

            if (includeStackTraceRef)
            {
                reportExceptionDetails(outputRef, curErrorInfo, loopCtr == 0 ? contextInfo : null);

                Throwable[] suppressedExceptions = curErrorInfo.getSuppressed();
                for (int supIdx = 0; supIdx < suppressedExceptions.length; ++supIdx)
                {
                    outputRef.println(
                        "Suppressed exception %d of %d:\n===============",
                        supIdx + 1,
                        suppressedExceptions.length
                    );

                    reportExceptionDetails(outputRef, suppressedExceptions[supIdx], loopCtr == 0 ? contextInfo : null);
                }
            }
            else
            {
                if (curErrorInfo.getMessage() != null)
                {
                    outputRef.println(ERROR_FIELD_FORMAT, "Error message:", curErrorInfo.getMessage());
                }
                if (curErrorInfo instanceof LinStorException)
                {
                    LinStorException linStorException = (LinStorException) curErrorInfo;
                    if (linStorException.hasErrorContext())
                    {
                        outputRef.println(linStorException.getErrorContext());
                    }
                }
            }
            ++loopCtr;
        }
        outputRef.println("\nEND OF ERROR REPORT.");
    }

    void reportAccessContext(ErrorReportRenderer output, AccessContext accCtx)
    {
        output.println("Access context information\n");
        output.printf(ERROR_FIELD_FORMAT, "Identity:", accCtx.subjectDomain.name.displayValue);
        output.printf(ERROR_FIELD_FORMAT, "Role:", accCtx.subjectRole.name.displayValue);
        output.printf(ERROR_FIELD_FORMAT, "Domain:", accCtx.subjectDomain.name.displayValue);
        output.println();
    }

    void reportPeer(ErrorReportRenderer output, Peer client)
    {
        String peerAddress = null;

        String peerId = client.toString();
        InetSocketAddress socketAddr = client.peerAddress();
        if (socketAddr != null)
        {
            InetAddress ipAddr = socketAddr.getAddress();
            if (ipAddr != null)
            {
                peerAddress = ipAddr.getHostAddress();
            }
        }

        if (peerId != null)
        {
            output.println("Connected peer information\n");
            output.printf(ERROR_FIELD_FORMAT, "Peer ID:", peerId);
            if (peerAddress == null)
            {
                output.println("The peer's network address is unknown.");
            }
            else
            {
                output.printf(ERROR_FIELD_FORMAT, "Network address:", peerAddress);
            }
        }
    }

    void reportHeader(
        ErrorReportRenderer output,
        long reportNr,
        @Nullable AccessContext accCtxRef,
        @Nullable Peer client,
        LocalDateTime errorTime
    )
    {
        output.printf("ERROR REPORT %s-%06d\n\n", instanceId, reportNr);
        output.println(SECTION_SEPARATOR);
        output.println();
        output.printf(ERROR_FIELD_FORMAT, "Application:", LinStor.SOFTWARE_CREATOR + " " + LinStor.PROGRAM);
        output.printf(ERROR_FIELD_FORMAT, "Module:", dmModule);
        output.printf(ERROR_FIELD_FORMAT, "Version:", LinStor.VERSION_INFO_PROVIDER.getVersion());
        output.printf(ERROR_FIELD_FORMAT, "Build ID:", LinStor.VERSION_INFO_PROVIDER.getGitCommitId());
        output.printf(ERROR_FIELD_FORMAT, "Build time:", LinStor.VERSION_INFO_PROVIDER.getBuildTime());
        output.printf(
            ERROR_FIELD_FORMAT,
            "Error time:",
            TimeUtils.JOURNALCTL_DF.withLocale(Locale.US).format(errorTime)
        );
        output.printf(ERROR_FIELD_FORMAT, "Node:", nodeName);
        output.printf(ERROR_FIELD_FORMAT, "Thread:", Thread.currentThread().getName());
        if (accCtxRef != null)
        {
            reportAccessContext(output, accCtxRef);
        }
        if (client != null)
        {
            output.printf(ERROR_FIELD_FORMAT, "Peer:", client.toString());
        }
        output.println();
        output.print(SECTION_SEPARATOR).print("\n\n");
    }

    boolean reportLinStorException(ErrorReportRenderer output, LinStorException lsExc)
    {
        boolean detailsAvailable = false;

        String descriptionMsg   = lsExc.getDescriptionText();
        String causeMsg         = lsExc.getCauseText();
        String correctionMsg    = lsExc.getCorrectionText();
        String detailsMsg       = lsExc.getDetailsText();

        if (descriptionMsg == null)
        {
            descriptionMsg = lsExc.getMessage();
        }

        if (descriptionMsg != null)
        {
            detailsAvailable = true;
            output.println("Description:");
            output.printlnWithIndent(descriptionMsg);
        }

        if (causeMsg != null)
        {
            detailsAvailable = true;
            output.println("Cause:");
            output.printlnWithIndent(causeMsg);
        }

        if (correctionMsg != null)
        {
            detailsAvailable = true;
            output.println("Correction:");
            output.printlnWithIndent(correctionMsg);
        }

        if (detailsMsg != null)
        {
            detailsAvailable = true;
            output.println("Additional information:");
            output.printlnWithIndent(detailsMsg);
        }

        if (detailsAvailable)
        {
            output.println();
        }

        return detailsAvailable;
    }

    void reportExceptionDetails(ErrorReportRenderer output, Throwable errorInfo, String contextInfo)
    {
        String category;
        if (errorInfo instanceof LinStorException)
        {
            category = "LinStorException";
        }
        else
        if (errorInfo instanceof RuntimeException)
        {
            category = "RuntimeException";
        }
        else
        if (errorInfo instanceof Exception)
        {
            category = "Exception";
        }
        else
        if (errorInfo instanceof Error)
        {
            category = "Error";
        }
        else
        {
            category = "Throwable";
        }

        // Determine exception class simple name
        String tClassName = UNKNOWN_LABEL;
        try
        {
            Class<? extends Throwable> tClass = errorInfo.getClass();
            tClassName = tClass.getSimpleName();
        }
        catch (Exception ignored)
        {
        }

        // Determine exception class canonical name
        String tFullClassName = UNKNOWN_LABEL;
        try
        {
            Class<? extends Throwable> tClass = errorInfo.getClass();
            String canName = tClass.getCanonicalName();
            if (canName != null)
            {
                tFullClassName = canName;
            }
        }
        catch (Exception ignored)
        {
        }

        // Determine the code location where the exception was generated
        String tGeneratedAt = UNKNOWN_LABEL;
        try
        {
            StackTraceElement[] traceItems = errorInfo.getStackTrace();
            if (traceItems != null)
            {
                if (traceItems.length >= 1)
                {
                    StackTraceElement topItem = traceItems[0];
                    if (topItem != null)
                    {
                        String methodName = topItem.getMethodName();
                        String fileName = topItem.getFileName();
                        int lineNumber = topItem.getLineNumber();

                        StringBuilder result = new StringBuilder();
                        result.append("Method '");
                        result.append(methodName);
                        result.append("'");

                        if (fileName != null)
                        {
                            if (result.length() > 0)
                            {
                                result.append(", ");
                            }
                            result.append("Source file '");
                            result.append(fileName);
                            if (lineNumber >= 0)
                            {
                                result.append("', Line #");
                                result.append(lineNumber);
                            }
                            else
                            {
                                result.append(", Unknown line number");
                            }
                        }
                        if (result.length() > 0)
                        {
                            tGeneratedAt = result.toString();
                        }
                    }
                }
            }
        }
        catch (Exception ignored)
        {
        }

        // Report information about the exception
        output.printf(ERROR_FIELD_FORMAT, "Category:", category);
        output.printf(ERROR_FIELD_FORMAT, "Class name:", tClassName);
        output.printf(ERROR_FIELD_FORMAT, "Class canonical name:", tFullClassName);
        output.printf(ERROR_FIELD_FORMAT, "Generated at:", tGeneratedAt);

        output.println();

        // Report the exception's message
        try
        {
            String msg = errorInfo.getMessage();
            if (msg != null)
            {
                output.printf(ERROR_FIELD_FORMAT, "Error message:", msg);
            }
        }
        catch (Exception ignored)
        {
        }

        output.println();

        if (contextInfo != null)
        {
            output.println("Error context:");
            output.increaseIndent();
            output.print(contextInfo);
            output.decreaseIndent();
            output.println();
        }

        if (errorInfo instanceof ErrorContextSupplier)
        {
            String context = ((ErrorContextSupplier) errorInfo).getErrorContext();
            if (context != null)
            {
                output.println(context);
            }
        }

        Throwable[] allSuppressed = errorInfo.getSuppressed();
        if (allSuppressed != null && allSuppressed.length > 0)
        {
            output.println("Asynchronous stage backtrace:");
            output.increaseIndent();
            for (Throwable suppressed : allSuppressed)
            {
                // Strip away reactor 'light checkpoint' details
                if (suppressed.getMessage() != null)
                {
                    Matcher matcher = LIGHT_CHECKPOINT_PATTERN.matcher(suppressed.getMessage());
                    if (matcher.find())
                    {
                        output.println(matcher.group(1));
                    }
                    else
                    {
                        output.println(suppressed.getMessage());
                    }
                }
                else
                {
                    output.println("null");
                }
            }
            output.decreaseIndent();
            output.println();
        }

        // Report the call backtrace
        reportBacktrace(output, errorInfo);
    }

    void reportBacktrace(ErrorReportRenderer output, Throwable errorInfo)
    {
        StackTraceElement[] trace = errorInfo.getStackTrace();
        if (printStackTraces)
        {
            errorInfo.printStackTrace();
            if (errorInfo instanceof ErrorContextSupplier)
            {
                System.err.println("ErrorContext: ");
                ErrorContextSupplier errCtxSup = (ErrorContextSupplier) errorInfo;
                String errCtx = errCtxSup.getErrorContext();
                if (errCtx != null)
                {
                    System.err.println(errCtx);
                }
            }
        }

        if (trace == null)
        {
            output.println("No call backtrace is available.");
        }
        else
        {
            output.println(
                "Call backtrace:\n\n" +
                    "    %-40s %-6s %s",
                "Method", "Native", "Class:Line number"
            );
            for (StackTraceElement traceItem : trace)
            {
                boolean nativeCode  = traceItem.isNativeMethod();
                String methodName   = traceItem.getMethodName();
                int numericLineNr      = traceItem.getLineNumber();
                String className    = traceItem.getClassName();

                String lineNr;
                if (numericLineNr >= 0)
                {
                    lineNr = Integer.toString(numericLineNr);
                }
                else
                {
                    lineNr = "unknown";
                }

                output.println(
                    "    %-40s %-6s %s:%s",
                    methodName, nativeCode ? "Y" : "N", className, lineNr
                );
            }
            output.println();
        }
    }
}
