import {
  assert,
  defaultWorkbenchPage,
  evaluate,
  runChromeSmoke,
  waitFor,
} from './lib/chrome-cdp.mjs';

const pageUrl = process.argv[2] || defaultWorkbenchPage();

function mockFetchExpression() {
  return `(() => {
    window.fetch = async (input) => {
      const url = String(input);
      if (url.includes('/api/agent/projects/intake')) {
        return new Response(JSON.stringify({
          success: true,
          project: {
            name: 'summary-smoke-project',
            rootPath: '/workspace/projects/summary-smoke-project',
            detectedStack: ['Java 21', 'Spring Boot', 'Docker Compose'],
            testCommands: ['mvn test']
          },
          intake: {
            currentGoal: 'Polish display density',
            currentStage: 'Workbench polish',
            takeoverBrief: 'Mocked takeover brief for summary smoke.',
            completed: ['Read project files'],
            unfinished: ['Continue UI polish'],
            risks: [],
            nextStepOnly: 'Inspect summary metrics',
            stackEvidence: ['pom.xml', 'docker-compose.yml'],
            runnableCommands: ['mvn test', 'docker compose up -d'],
            verificationGaps: ['Live Docker rebuild pending'],
            workingTree: {
              isGitRepository: true,
              hasUncommittedChanges: false,
              statusShort: '',
              latestCommits: ['abc123 test commit']
            }
          },
          evidence: [{ type: 'README', path: 'README.md', summary: 'Project overview' }],
          readLog: { readFiles: ['README.md', 'SPEC.md'], commands: ['git status --short'] },
          warnings: []
        }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (url.includes('/api/agent/code/search')) {
        return new Response(JSON.stringify({
          rootPath: '/workspace/projects/summary-smoke-project',
          query: 'StaticWorkbenchHtmlTest',
          gitRepository: true,
          matches: [
            { path: 'src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java', lineNumber: 78, line: 'function renderCompactMetric(label, value, tone)' },
            { path: 'src/main/resources/static/index.html', lineNumber: 937, line: 'function renderCompactMetric(label, value, tone)' }
          ],
          skippedFiles: [{ path: 'target/generated.log', reason: 'ignored' }],
          warnings: ['mock warning']
        }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (url.includes('/api/agent/eval/runs')) {
        return new Response(JSON.stringify([
          {
            caseId: 'E-SUMMARY-1',
            title: 'Passed eval summary case',
            status: 'PASSED',
            score: 9,
            maxScore: 10,
            humanInterventions: 0,
            durationSeconds: 12,
            summary: 'Eval run summary smoke passed.'
          },
          {
            caseId: 'E-SUMMARY-2',
            title: 'Partial eval summary case',
            status: 'PARTIAL',
            score: 5,
            maxScore: 10,
            humanInterventions: 1,
            durationSeconds: 30,
            summary: 'Eval run summary smoke partial.'
          },
          {
            caseId: 'E-SUMMARY-3',
            title: 'Failed eval summary case',
            status: 'FAILED',
            score: 1,
            maxScore: 10,
            humanInterventions: 2,
            durationSeconds: 45,
            summary: 'Eval run summary smoke failed.',
            failureReason: 'Mock failure reason'
          }
        ]), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (url.includes('/api/agent/eval/report')) {
        return new Response(JSON.stringify({
          projectId: 'summary-smoke-project',
          totalRuns: 3,
          passedRuns: 1,
          partialRuns: 1,
          failedRuns: 1,
          successRate: 0.333,
          scoreRate: 0.5,
          averageDurationSeconds: 29,
          totalHumanInterventions: 3,
          totalToolCallCount: 6,
          cases: [
            { caseId: 'E-SUMMARY-1', title: 'Passed eval summary case', latestStatus: 'PASSED' }
          ],
          failureReasons: [
            { reason: 'Mock failure reason', count: 1 }
          ]
        }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      throw new Error('Unexpected mocked fetch: ' + url);
    };
  })()`;
}

async function runSmoke(cdp) {
  await waitFor(cdp, 'Boolean(document.getElementById("workspaceNavProjectIntake"))');
  const hasSummaryHelper = await evaluate(cdp, 'typeof renderCompactMetric === "function"');
  assert(hasSummaryHelper, 'renderCompactMetric helper was not found on the page');
  await evaluate(cdp, mockFetchExpression());

  await evaluate(cdp, `(() => {
    document.getElementById('workspaceNavProjectIntake').click();
    document.getElementById('projectIntakePath').value = '/workspace/projects/summary-smoke-project';
    document.getElementById('projectIntakeButton').click();
  })()`);
  await waitFor(cdp, `(() => {
    const result = document.getElementById('projectIntakeResult');
    return !result.classList.contains('hidden') && result.textContent.includes('summary-smoke-project');
  })()`);

  const projectSummary = await evaluate(cdp, `(() => {
    const summary = document.querySelector('#projectIntakeResult > div.mb-3.grid');
    return {
      cardCount: summary?.children.length || 0,
      values: [...(summary?.children || [])].map((card) => card.querySelector('p:last-child')?.textContent.trim()),
      visible: !document.getElementById('projectIntakeResult').classList.contains('hidden')
    };
  })()`);
  assert(projectSummary.visible, 'Project Intake result did not become visible');
  assert(projectSummary.cardCount === 4, `Expected 4 Project Intake summary cards: ${JSON.stringify(projectSummary)}`);
  assert(projectSummary.values.join(',') === '3,2,1,0', `Unexpected Project Intake metrics: ${JSON.stringify(projectSummary)}`);

  await evaluate(cdp, `(() => {
    document.getElementById('workspaceNavCodeContext').click();
    document.getElementById('codeContextMode').value = 'search';
    document.getElementById('codeContextQuery').value = 'StaticWorkbenchHtmlTest';
    document.getElementById('codeContextButton').click();
  })()`);
  await waitFor(cdp, `(() => {
    const result = document.getElementById('codeContextResult');
    return !result.classList.contains('hidden') && result.textContent.includes('StaticWorkbenchHtmlTest');
  })()`);

  const codeSummary = await evaluate(cdp, `(() => {
    const summary = document.querySelector('#codeContextResult > div.mb-3.grid');
    return {
      cardCount: summary?.children.length || 0,
      values: [...(summary?.children || [])].map((card) => card.querySelector('p:last-child')?.textContent.trim()),
      visible: !document.getElementById('codeContextResult').classList.contains('hidden')
    };
  })()`);
  assert(codeSummary.visible, 'Code Context result did not become visible');
  assert(codeSummary.cardCount === 4, `Expected 4 Code Context summary cards: ${JSON.stringify(codeSummary)}`);
  assert(codeSummary.values.join(',') === '2,1,1,yes', `Unexpected Code Context metrics: ${JSON.stringify(codeSummary)}`);

  await evaluate(cdp, `(async () => {
    document.getElementById('workspaceNavEval').click();
    openWorkspaceSubTab('eval', 'evalRunList');
    await loadEvalData();
  })()`);
  await waitFor(cdp, `Boolean(document.querySelector('#evalRunList > div.mb-3.grid'))`);

  const evalSummary = await evaluate(cdp, `(() => {
    const summary = document.querySelector('#evalRunList > div.mb-3.grid');
    return {
      cardCount: summary?.children.length || 0,
      values: [...(summary?.children || [])].map((card) => card.querySelector('p:last-child')?.textContent.trim()),
      visible: !document.getElementById('evalRunList').classList.contains('hidden')
    };
  })()`);
  assert(evalSummary.visible, 'Eval run list did not become visible');
  assert(evalSummary.cardCount === 4, `Expected 4 Eval summary cards: ${JSON.stringify(evalSummary)}`);
  assert(evalSummary.values.join(',') === '3,1,1,1', `Unexpected Eval summary metrics: ${JSON.stringify(evalSummary)}`);

  const layout = await evaluate(cdp, `(() => ({
    viewportWidth: window.innerWidth,
    documentScrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    overflow: document.documentElement.scrollWidth > window.innerWidth || document.body.scrollWidth > window.innerWidth
  }))()`);
  assert(!layout.overflow, `Summary smoke caused horizontal overflow: ${JSON.stringify(layout)}`);

  return { pageUrl, projectSummary, codeSummary, evalSummary, layout };
}

runChromeSmoke(pageUrl, { profileName: 'workbench-summary', width: 390, height: 844 }, runSmoke)
  .then((result) => {
    console.log(JSON.stringify({ ok: true, ...result }, null, 2));
  })
  .catch((error) => {
    console.error(error.stack || error.message);
    process.exit(1);
  });
