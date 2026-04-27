package de.envite.connector.ibmq.model;

/**
 * Determines which operation the IBMQ connector performs.
 *
 * <ul>
 *   <li>{@link #SUBMIT_JOB} – authenticates, submits a Qiskit Runtime job, and optionally polls
 *       until it reaches a terminal state.</li>
 *   <li>{@link #GET_JOB_RESULT} – authenticates, checks the current status of an existing job,
 *       and returns its result if the job has completed. Intended for use inside a BPMN polling
 *       loop driven by a timer intermediate event.</li>
 * </ul>
 */
public enum OperationMode {
  SUBMIT_JOB,
  GET_JOB_RESULT
}
