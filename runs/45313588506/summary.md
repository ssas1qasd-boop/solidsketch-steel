# Emulator UI run 45313588506 (c41be40)

- data files on main: true
- KVM: yes · runner cores: 2
- unit tests incl. UI golden (gate before the APK): success
- build APK + test APK: success
- emulator tests step: success
- connectedDebugAndroidTest exit code: 1 (295 s)
- job time until this step: 1064 s = 17.7 min (billed: whole minutes, rounded up per job)

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
orbit: timed out waiting for the view to orbit
column: timed out waiting for a column
properties_apply: timed out waiting for the property sheet
beam: timed out waiting for a beam
properties_modify: List is empty.
catalog: timed out waiting for the catalog
endplate_dialog: timed out waiting for the End plate (144) dialog
endplate_pick: Collection contains no element matching the predicate.
at org.junit.Assert.fail(Assert.java:89)
at org.junit.Assert.assertTrue(Assert.java:42)
at com.solidsketchapp.steel.EmulatorFlowTest.ownerWorkflow(EmulatorFlowTest.kt:216)

```

| step | ok | ms | message |
|---|---|---|---|
| start | ✓ | 3513 |  |
| steel | ✓ | 10919 |  |
| grid | ✓ | 13293 |  |
| orbit | ✗ | 6384 | timed out waiting for the view to orbit |
| pan | ✓ | 840 |  |
| zoom | ✓ | 961 |  |
| column | ✗ | 21582 | timed out waiting for a column |
| properties_apply | ✗ | 21588 | timed out waiting for the property sheet |
| beam | ✗ | 20280 | timed out waiting for a beam |
| properties_modify | ✗ | 201 | List is empty. |
| catalog | ✗ | 20212 | timed out waiting for the catalog |
| endplate_dialog | ✗ | 20234 | timed out waiting for the End plate (144) dialog |
| endplate_pick | ✗ | 187 | Collection contains no element matching the predicate. |
| save_open | ✓ | 1135 |  |

connected.log (end):
```
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:39)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:46)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:34)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:48)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:35)
	at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:61)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:127)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:116)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:209)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:331)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:318)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:314)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:85)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:314)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)


BUILD FAILED in 4m 53s
133 actionable tasks: 6 executed, 127 up-to-date

```
