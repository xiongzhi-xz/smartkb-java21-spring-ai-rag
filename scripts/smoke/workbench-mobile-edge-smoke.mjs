import {
  assert,
  defaultWorkbenchPage,
  evaluate,
  runChromeSmoke,
  waitFor,
} from './lib/chrome-cdp.mjs';

const pageUrl = process.argv[2] || defaultWorkbenchPage();
const longToken = 'LONG-MOBILE-EDGE-TEXT-'.repeat(18);
const longSentence = `${longToken} keeps narrow panels honest without changing backend state.`;

function mockMobileEdgeFetchExpression() {
  return `(() => {
    window.fetch = async (input) => {
      const url = String(input);
      if (url.includes('/api/agent/memories')) {
        return new Response(JSON.stringify([
          {
            id: 'M-EDGE-1',
            projectId: 'smartkb-edge',
            authorityLevel: 'HIGH',
            sourceType: 'SPEC',
            sourcePath: 'SPEC.md',
            content: 'High authority memory for mobile edge smoke.',
            tags: ['mobile', 'layout']
          },
          {
            id: 'M-EDGE-2',
            projectId: 'smartkb-edge',
            authorityLevel: 'HIGH',
            sourceType: 'HANDOFF',
            sourcePath: 'HANDOFF.md',
            content: 'Second high authority memory for summary counts.',
            tags: ['mobile', 'handoff']
          },
          {
            id: 'M-EDGE-3',
            projectId: 'smartkb-edge',
            authorityLevel: 'MEDIUM',
            sourceType: 'SPEC',
            sourcePath: 'docs/notes.md',
            content: 'Medium authority memory for source-type counts.',
            tags: []
          }
        ]), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      throw new Error('Unexpected mocked fetch: ' + url);
    };
  })()`;
}

async function assertNoOverflow(cdp, label) {
  const layout = await evaluate(cdp, `(() => {
    const viewportWidth = window.innerWidth;
    const wideElements = [...document.querySelectorAll('body *')]
      .filter((element) => {
        const style = window.getComputedStyle(element);
        if (style.display === 'none' || style.visibility === 'hidden') return false;
        const rect = element.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) return false;
        return rect.right > viewportWidth + 1 || rect.left < -1;
      })
      .slice(0, 8)
      .map((element) => ({
        id: element.id || '',
        tag: element.tagName.toLowerCase(),
        className: String(element.className || '').slice(0, 80),
        left: Math.round(element.getBoundingClientRect().left),
        right: Math.round(element.getBoundingClientRect().right),
        width: Math.round(element.getBoundingClientRect().width)
      }));
    return {
      label: ${JSON.stringify(label)},
      viewportWidth,
      documentScrollWidth: document.documentElement.scrollWidth,
      bodyScrollWidth: document.body.scrollWidth,
      overflow: document.documentElement.scrollWidth > viewportWidth || document.body.scrollWidth > viewportWidth,
      wideElements
    };
  })()`);
  assert(!layout.overflow, `${label} created page overflow: ${JSON.stringify(layout)}`);
  assert(layout.wideElements.length === 0, `${label} has elements outside viewport: ${JSON.stringify(layout)}`);
  return layout;
}

async function visibleText(cdp, selector) {
  return evaluate(cdp, `(() => {
    const element = document.querySelector(${JSON.stringify(selector)});
    return element && !element.classList.contains('hidden') ? element.innerText : '';
  })()`);
}

async function runSmoke(cdp) {
  await waitFor(cdp, 'Boolean(document.getElementById("workspaceNavProjectIntake"))');
  await evaluate(cdp, mockMobileEdgeFetchExpression());
  await assertNoOverflow(cdp, 'initial chat workspace');

  const results = [];

  await evaluate(cdp, `(() => {
    document.getElementById('workspaceNavProjectIntake').click();
    document.getElementById('projectIntakePath').value = '';
    document.getElementById('projectIntakeGoal').value = ${JSON.stringify(longSentence)};
    document.getElementById('projectIntakeButton').click();
  })()`);
  await waitFor(cdp, `document.getElementById('projectIntakeResult').innerText.length > 0`);
  assert((await visibleText(cdp, '#projectIntakeResult')).length > 0, 'Project Intake validation message did not render');
  results.push(await assertNoOverflow(cdp, 'project intake validation'));

  await evaluate(cdp, `(() => {
    document.getElementById('projectIntakePath').value = '/workspace/projects/${longToken}';
  })()`);
  results.push(await assertNoOverflow(cdp, 'project intake long path'));

  await evaluate(cdp, `(() => {
    document.getElementById('workspaceNavAgentTask').click();
    openWorkspaceSubTab('agentTask', 'agentTaskCreateTab');
    document.getElementById('agentTaskProjectId').value = ${JSON.stringify(`smartkb-${longToken}`)};
    document.getElementById('agentTaskTitle').value = '';
    document.getElementById('agentTaskGoal').value = '';
    document.getElementById('agentTaskCreateButton').click();
  })()`);
  await waitFor(cdp, `document.getElementById('agentTaskCreateResult').innerText.length > 0`);
  assert((await visibleText(cdp, '#agentTaskCreateResult')).length > 0, 'AgentTask validation message did not render');
  results.push(await assertNoOverflow(cdp, 'agent task empty validation'));

  await evaluate(cdp, `(() => {
    document.getElementById('agentTaskTitle').value = ${JSON.stringify(longSentence)};
    document.getElementById('agentTaskGoal').value = ${JSON.stringify(`${longSentence} ${longSentence}`)};
  })()`);
  results.push(await assertNoOverflow(cdp, 'agent task long fields'));

  await evaluate(cdp, `(() => {
    document.getElementById('workspaceNavMemory').click();
    document.getElementById('memoryProjectId').value = '';
    document.getElementById('memoryContent').value = '';
    document.getElementById('memoryCreateButton').click();
  })()`);
  await waitFor(cdp, `Boolean(document.querySelector('#memoryList > div.mb-3.grid'))`);
  const memorySummary = await evaluate(cdp, `(() => {
    const summary = document.querySelector('#memoryList > div.mb-3.grid');
    return {
      cardCount: summary?.children.length || 0,
      values: [...(summary?.children || [])].map((card) => card.querySelector('p:last-child')?.textContent.trim())
    };
  })()`);
  assert(memorySummary.cardCount === 4, `Expected 4 memory summary cards: ${JSON.stringify(memorySummary)}`);
  assert(memorySummary.values.join(',') === '3,2,2,3', `Unexpected memory summary metrics: ${JSON.stringify(memorySummary)}`);
  await waitFor(cdp, `document.getElementById('memoryActionResult').innerText.length > 0`);
  assert((await visibleText(cdp, '#memoryActionResult')).length > 0, 'Memory validation message did not render');
  results.push(await assertNoOverflow(cdp, 'memory validation'));

  await evaluate(cdp, `(() => {
    document.getElementById('memoryProjectId').value = ${JSON.stringify(`smartkb-${longToken}`)};
    document.getElementById('memoryRootPath').value = ${JSON.stringify(`/workspace/projects/${longToken}`)};
    document.getElementById('memorySourcePath').value = ${JSON.stringify(`docs/${longToken}/decision-record.md`)};
    document.getElementById('memoryTags').value = ${JSON.stringify(`mobile,${longToken}`)};
    document.getElementById('memoryContent').value = ${JSON.stringify(`${longSentence}\\n${longSentence}`)};
    document.getElementById('memoryConflictContent').value = ${JSON.stringify(longSentence)};
  })()`);
  results.push(await assertNoOverflow(cdp, 'memory long fields'));

  await evaluate(cdp, `(() => {
    document.getElementById('workspaceNavCodeContext').click();
    document.getElementById('codeContextPath').value = ${JSON.stringify(`/workspace/projects/${longToken}`)};
    document.getElementById('codeContextMode').value = 'search';
    document.getElementById('codeContextQuery').value = '';
    document.getElementById('codeContextButton').click();
  })()`);
  await waitFor(cdp, `document.getElementById('codeContextResult').innerText.length > 0`);
  assert((await visibleText(cdp, '#codeContextResult')).length > 0, 'Code Context validation message did not render');
  results.push(await assertNoOverflow(cdp, 'code context query validation'));

  await evaluate(cdp, `(() => {
    document.getElementById('codeContextQuery').value = ${JSON.stringify(longToken)};
  })()`);
  results.push(await assertNoOverflow(cdp, 'code context long fields'));

  await evaluate(cdp, `(() => {
    document.getElementById('workspaceNavEval').click();
    openWorkspaceSubTab('eval', 'evalCreateTab');
    document.getElementById('evalCaseId').value = '';
    document.getElementById('evalTitle').value = '';
    document.getElementById('evalCreateButton').click();
  })()`);
  await waitFor(cdp, `document.getElementById('evalResult').innerText.length > 0`);
  assert((await visibleText(cdp, '#evalResult')).length > 0, 'Eval validation message did not render');
  results.push(await assertNoOverflow(cdp, 'eval validation'));

  await evaluate(cdp, `(() => {
    document.getElementById('evalProjectId').value = ${JSON.stringify(`smartkb-${longToken}`)};
    document.getElementById('evalCaseId').value = ${JSON.stringify(`E-${longToken}`)};
    document.getElementById('evalTitle').value = ${JSON.stringify(longSentence)};
    document.getElementById('evalEvidencePaths').value = ${JSON.stringify(`docs/${longToken}.md\\ntarget/${longToken}.log`)};
    document.getElementById('evalVerificationCommands').value = ${JSON.stringify(`mvn -Dtest=${longToken} test\\ngit diff --check`)};
    document.getElementById('evalSummary').value = ${JSON.stringify(longSentence)};
    document.getElementById('evalFailureReason').value = ${JSON.stringify(longSentence)};
  })()`);
  results.push(await assertNoOverflow(cdp, 'eval long fields'));

  const stableControls = await evaluate(cdp, `(() => {
    const selectors = [
      '#workspaceNavProjectIntake',
      '#workspaceNavAgentTask',
      '#workspaceNavMemory',
      '#workspaceNavCodeContext',
      '#workspaceNavEval',
      '#evalCreateButton'
    ];
    return selectors.map((selector) => {
      const element = document.querySelector(selector);
      const rect = element?.getBoundingClientRect();
      return {
        selector,
        exists: Boolean(element),
        width: rect ? Math.round(rect.width) : 0,
        height: rect ? Math.round(rect.height) : 0,
        withinViewport: rect ? rect.left >= -1 && rect.right <= window.innerWidth + 1 : false
      };
    });
  })()`);
  stableControls.forEach((control) => {
    assert(control.exists, `Missing control ${control.selector}`);
    assert(control.width > 0 && control.height > 0, `Collapsed control ${JSON.stringify(control)}`);
    assert(control.withinViewport, `Control outside viewport ${JSON.stringify(control)}`);
  });

  return {
    pageUrl,
    viewport: { width: 390, height: 844 },
    checkedStates: results.length,
    stableControls,
    finalLayout: results[results.length - 1],
  };
}

runChromeSmoke(pageUrl, { profileName: 'workbench-mobile-edge', width: 390, height: 844 }, runSmoke)
  .then((result) => {
    console.log(JSON.stringify({ ok: true, ...result }, null, 2));
  })
  .catch((error) => {
    console.error(error.stack || error.message);
    process.exit(1);
  });
