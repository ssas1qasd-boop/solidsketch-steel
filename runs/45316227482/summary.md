# Emulator UI run 45316227482 (becd9e1)

- data files on main: true
- KVM: yes · runner cores: 2
- unit tests incl. UI golden (gate before the APK): success
- build APK + test APK: success
- emulator tests step: success
- connectedDebugAndroidTest exit code: 1 (292 s)
- job time until this step: 860 s = 14.3 min (billed: whole minutes, rounded up per job)

Device:
```
Physical size: 1080x2400
Override size: 1080x2340
Physical density: 420
Night mode: yes
[x] com.android.internal.systemui.navbar.threebutton
[ ] com.android.internal.systemui.navbar.twobutton
[ ] com.android.internal.systemui.navbar.transparent
[ ] com.android.internal.systemui.navbar.gestural
```

JUnit (unit + instrumented): 36 tests, 1 failed

```
com.solidsketchapp.steel.EmulatorFlowTest.ownerWorkflow: java.lang.AssertionError: failed steps:
column: Action performScrollTo() failed.
Reason: Expected exactly '1' node but found '2' nodes that satisfy: ((Text + EditableText contains 'Column' (ignoreCase: false)) && (OnClick is defined))
Nodes found:
1) Node #50 at (l=504.0, t=1912.0, r=705.0, b=1996.0)px
Focused = 'false'
Text = '[Column]'
Actions = [OnClick, RequestFocus, SetTextSubstitution, ShowTextSubstitution, ClearTextSubstitution, GetTextLayoutResult]
MergeDescendants = 'true'
Has 12 siblings
2) Node #118 at (l=860.0, t=2012.0, r=1061.0, b=2096.0)px
Focused = 'false'
Text = '[Column]'
Actions = [OnClick, RequestFocus, SetTextSubstitution, ShowTextSubstitution, ClearTextSubstitution, GetTextLayoutResult]
MergeDescendants = 'true'
Has 5 siblings

properties_apply: Failed to perform text input.
Reason: Expected exactly '1' node but could not find any node that satisfies: ((SetText is defined) && (value IPE300))

beam: timed out waiting for a beam
properties_modify: Failed to perform text input.
Reason: Expected exactly '1' node but could not find any node that satisfies: ((SetText is defined) && (value IPE400))

catalog: timed out waiting for the catalog
endplate_dialog: timed out waiting for the End plate (144) dialog
endplate_pick: timed out waiting for an End plate (144)
at org.junit.Assert.fail(Assert.java:89)
at org.junit.Assert.assertTrue(Assert.java:42)
at com.solidsketchapp.steel.EmulatorFlowTest.ownerWorkflow(EmulatorFlowTest.kt:222)

```

| step | ok | ms | message |
|---|---|---|---|
| start | ✓ | 1726 |  |
| steel | ✓ | 9420 |  |
| grid | ✓ | 5489 |  |
| orbit | ✓ | 1325 |  |
| pan | ✓ | 1402 |  |
| zoom | ✓ | 1485 |  |
| column | ✗ | 4522 | Action performScrollTo() failed. Reason: Expected exactly '1' node but found '2' nodes that satisfy: ((Text + EditableText contains 'Column' (ignoreCase: false)) && (OnClick is defined)) Nodes found: 1) Node #50 at (l=504.0, t=1912.0, r=705.0, b=1996.0)px Focused = 'false' Text = '[Column]' Actions  |
| properties_apply | ✗ | 5834 | Failed to perform text input. Reason: Expected exactly '1' node but could not find any node that satisfies: ((SetText is defined) && (value IPE300))  |
| beam | ✗ | 20715 | timed out waiting for a beam |
| properties_modify | ✗ | 1409 | Failed to perform text input. Reason: Expected exactly '1' node but could not find any node that satisfies: ((SetText is defined) && (value IPE400))  |
| catalog | ✗ | 20981 | timed out waiting for the catalog |
| endplate_dialog | ✗ | 20329 | timed out waiting for the End plate (144) dialog |
| endplate_pick | ✗ | 20965 | timed out waiting for an End plate (144) |
| save_open | ✓ | 2882 |  |

connected.log (end):
```
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:85)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:314)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)
	at org.gradle.execution.plan.DefaultPlanExecutor.process(DefaultPlanExecutor.java:111)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph.executeWithServices(DefaultTaskExecutionGraph.java:138)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph.execute(DefaultTaskExecutionGraph.java:123)
	at org.gradle.execution.SelectedTaskExecutionAction.execute(SelectedTaskExecutionAction.java:35)
	at org.gradle.execution.DryRunBuildExecutionAction.execute(DryRunBuildExecutionAction.java:51)
	at org.gradle.execution.BuildOperationFiringBuildWorkerExecutor$ExecuteTasks.call(BuildOperationFiringBuildWorkerExecutor.java:54)
	at org.gradle.execution.BuildOperationFiringBuildWorkerExecutor$ExecuteTasks.call(BuildOperationFiringBuildWorkerExecutor.java:43)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:209)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.execution.BuildOperationFiringBuildWorkerExecutor.execute(BuildOperationFiringBuildWorkerExecutor.java:40)
	at org.gradle.internal.build.DefaultBuildLifecycleController.lambda$executeTasks$10(DefaultBuildLifecycleController.java:313)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:266)
	at org.gradle.internal.model.StateTransitionController.lambda$tryTransition$8(StateTransitionController.java:177)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:44)
	at org.gradle.internal.model.StateTransitionController.tryTransition(StateTransitionController.java:177)
	at org.gradle.internal.build.DefaultBuildLifecycleController.executeTasks(DefaultBuildLifecycleController.java:304)
	at org.gradle.internal.build.DefaultBuildWorkGraphController$DefaultBuildWorkGraph.runWork(DefaultBuildWorkGraphController.java:220)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:263)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)
	at org.gradle.composite.internal.DefaultBuildController.doRun(DefaultBuildController.java:181)
	at org.gradle.composite.internal.DefaultBuildController.access$000(DefaultBuildController.java:50)
	at org.gradle.composite.internal.DefaultBuildController$BuildOpRunnable.lambda$run$0(DefaultBuildController.java:198)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:85)
	at org.gradle.composite.internal.DefaultBuildController$BuildOpRunnable.run(DefaultBuildController.java:198)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)


BUILD FAILED in 4m 50s
133 actionable tasks: 6 executed, 127 up-to-date

```
