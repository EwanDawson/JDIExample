package net.lazygun.example.jdi;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;

public class JDIExampleDebugger {
    private Class debugClass;
    private int[] breakPointLines;

    public static void main(String[] args) throws IOException {

        JDIExampleDebugger debuggerInstance = new JDIExampleDebugger();
        debuggerInstance.setDebugClass(JDIExampleTarget.class);
        int[] breakPoints = {6, 9};
        debuggerInstance.setBreakPointLines(breakPoints);
        VirtualMachine vm = null;
        try {
            vm = debuggerInstance.connectAndLaunchVM();
            vm.setDebugTraceMode(VirtualMachine.TRACE_NONE);
            System.out.println("VM description: " + vm.description());
            System.out.println("VM name: " + vm.name());
            System.out.println("VM version: " + vm.version());
            debuggerInstance.enableClassPrepareRequest(vm);
            EventSet eventSet;
            while ((eventSet = vm.eventQueue().remove()) != null) {
                for (Event event : eventSet) {
                    if (event instanceof ClassPrepareEvent) {
                        debuggerInstance.setBreakPoints(vm, (ClassPrepareEvent)event);
                    } else {
                        System.out.println(event);
                    }
                    if (event instanceof BreakpointEvent) {
                        debuggerInstance.displayVariables((BreakpointEvent) event);
                    }
                    vm.resume();
                }
            }
        } catch (VMDisconnectedException e) {
            System.out.println("Virtual Machine is disconnected.");
        } catch (VMStartException e) {
            consumeProcessOutput(e.process());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (vm != null) {
                Process process = vm.process();
                consumeProcessOutput(process);
            }
        }
    }

    private static void consumeProcessOutput(Process process) throws IOException {
        InputStreamReader reader = new InputStreamReader(process.getInputStream());
        OutputStreamWriter writer = new OutputStreamWriter(System.out);
        char[] buffer = new char[1024];
        reader.read(buffer);
        writer.write(buffer);
        writer.flush();
        reader = new InputStreamReader(process.getErrorStream());
        writer = new OutputStreamWriter(System.err);
        buffer = new char[1024];
        reader.read(buffer);
        writer.write(buffer);
        writer.flush();
    }

    private void setBreakPointLines(int[] breakPoints) {
        this.breakPointLines = breakPoints;
    }

    private void setDebugClass(Class<JDIExampleTarget> debugClass) {
        this.debugClass = debugClass;
    }

    public VirtualMachine connectAndLaunchVM() throws Exception {

        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager()
                .defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        arguments.get("options").setValue("-truffle -classpath out\\production\\Target -XX:+IgnoreUnrecognizedVMOptions");
        return launchingConnector.launch(arguments);
    }

    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }

    public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException {
        ClassType classType = (ClassType) event.referenceType();
        for(int lineNumber: breakPointLines) {
            Location location = classType.locationsOfLine(lineNumber).get(0);
            BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
            bpReq.enable();
        }
    }

    public void displayVariables(LocatableEvent event) throws IncompatibleThreadStateException,
            AbsentInformationException {
        StackFrame stackFrame = event.thread().frame(0);
        if(stackFrame.location().toString().contains(debugClass.getName())) {
            Map<LocalVariable, Value> visibleVariables = stackFrame
                    .getValues(stackFrame.visibleVariables());
            System.out.println("Variables at " + stackFrame.location().toString() +  " > ");
            for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                System.out.println(entry.getKey().name() + " = " + entry.getValue());
            }
        }
    }
}