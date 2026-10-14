const fs = require('fs');
const path = require('path');

const outDir = __dirname;
const collectionPath = path.join(outDir, 'YourVoice.verified.postman_collection.json');
const environmentPath = path.join(outDir, 'YourVoice.verified-local.postman_environment.json');
const compatibilityCollectionPath = path.join(outDir, 'YourVoice.postman_collection.json');
const compatibilityEnvironmentPath = path.join(outDir, 'YourVoice.local.postman_environment.json');

function event(listen, exec) {
  return { listen, script: { type: 'text/javascript', exec } };
}

function jsonBody(value) {
  return { mode: 'raw', raw: JSON.stringify(value, null, 2) };
}

function rawBody(raw) {
  return { mode: 'raw', raw };
}

function headers(...entries) {
  return entries.map(([key, value]) => ({ key, value }));
}


// Voter side of the RSA blind-signature protocol for Postman's sandbox (BigInt + CryptoJS, hex encodings).
// Mirrors frontend/src/utils/blindSignature.ts and BlindSignatureService.
const blindSignatureHelpers = [
  "const CryptoJS = require('crypto-js');",
  "function b64uToHex(value) { const binary = atob(value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=')); let hex = ''; for (let i = 0; i < binary.length; i++) { hex += binary.charCodeAt(i).toString(16).padStart(2, '0'); } return hex; }",
  "function hexToB64u(hex) { const even = hex.length % 2 ? '0' + hex : hex; let binary = ''; for (let i = 0; i < even.length; i += 2) { binary += String.fromCharCode(parseInt(even.substr(i, 2), 16)); } return btoa(binary).replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=+$/, ''); }",
  "function hexToBig(hex) { return BigInt('0x' + (hex || '0')); }",
  "function bigToHex(value) { const hex = value.toString(16); return hex.length % 2 ? '0' + hex : hex; }",
  "function byteLength(value) { return Math.ceil(value.toString(2).length / 8); }",
  "function modPow(base, exponent, modulus) { let result = 1n; let factor = base % modulus; let remaining = exponent; while (remaining > 0n) { if (remaining & 1n) { result = (result * factor) % modulus; } factor = (factor * factor) % modulus; remaining >>= 1n; } return result; }",
  "function modInverse(value, modulus) { let oldR = value % modulus, r = modulus, oldS = 1n, s = 0n; while (r !== 0n) { const q = oldR / r; [oldR, r] = [r, oldR - q * r]; [oldS, s] = [s, oldS - q * s]; } if (oldR !== 1n) { throw new Error('No modular inverse'); } return ((oldS % modulus) + modulus) % modulus; }",
  "function gcd(a, b) { while (b !== 0n) { [a, b] = [b, a % b]; } return a; }",
  "function fullDomainHash(tokenDomain, contestId, ballotToken, modulus) { const seedHex = CryptoJS.enc.Hex.stringify(CryptoJS.enc.Utf8.parse(tokenDomain + '|' + contestId + '|' + ballotToken)); const lengthHex = (byteLength(modulus) + 16) * 2; let expanded = ''; for (let counter = 0; expanded.length < lengthHex; counter++) { expanded += CryptoJS.SHA256(CryptoJS.enc.Hex.parse(seedHex + counter.toString(16).padStart(8, '0'))).toString(CryptoJS.enc.Hex); } return hexToBig(expanded.slice(0, lengthHex)) % modulus; }",
  "function randomHex(bytes) { return CryptoJS.lib.WordArray.random(bytes).toString(CryptoJS.enc.Hex); }"
];

const jsonHeaders = headers(['Content-Type', 'application/json']);
const adminJsonHeaders = headers(['Content-Type', 'application/json'], ['Authorization', 'Bearer {{adminToken}}']);
const voterJsonHeaders = headers(['Content-Type', 'application/json'], ['Authorization', 'Bearer {{voterToken}}']);
const adminAuthHeader = headers(['Authorization', 'Bearer {{adminToken}}']);
const voterAuthHeader = headers(['Authorization', 'Bearer {{voterToken}}']);

function requireVars(names) {
  return [
    `var requiredVariables = ${JSON.stringify(names)};`,
    'var unresolvedStart = String.fromCharCode(123, 123);',
    'var unresolvedEnd = String.fromCharCode(125, 125);',
    'var missingVariables = requiredVariables.filter((key) => {',
    '  const value = pm.environment.get(key);',
    '  return !value || value.includes(unresolvedStart) || value.includes(unresolvedEnd);',
    '});',
    'if (missingVariables.length > 0) {',
    "  throw new Error(`Missing Postman environment value(s): ${missingVariables.join(', ')}.`);",
    '}'
  ];
}

function expectStatus(status, label) {
  return [
    `pm.test('${label}', function () {`,
    `  pm.response.to.have.status(${status});`,
    '});',
    `if (pm.response.code !== ${status}) {`,
    `  throw new Error(\`${label} failed (\${pm.response.code}): \${pm.response.text()}\`);`,
    '}'
  ];
}

function expectOneOf(statuses, label) {
  return [
    `const allowedStatuses = ${JSON.stringify(statuses)};`,
    `pm.test('${label}', function () {`,
    '  pm.expect(allowedStatuses).to.include(pm.response.code);',
    '});',
    'if (!allowedStatuses.includes(pm.response.code)) {',
    `  throw new Error(\`${label} failed (\${pm.response.code}): \${pm.response.text()}\`);`,
    '}'
  ];
}

function storeId(variableName, label) {
  return [
    'const json = pm.response.json();',
    `if (!json.id) { throw new Error('${label} response did not include id.'); }`,
    `pm.environment.set('${variableName}', json.id);`
  ];
}

function storeToken(prefix, label) {
  return [
    'const json = pm.response.json();',
    `if (!json.accessToken) { throw new Error('${label} response did not include accessToken.'); }`,
    `pm.environment.set('${prefix}Token', json.accessToken);`,
    `pm.environment.set('${prefix}UserId', json.userId);`
  ];
}

function request({ name, method, url, header = [], body, prerequest = [], test = [], description }) {
  const item = {
    name,
    event: [],
    request: { method, header, url },
    response: []
  };
  if (description) item.request.description = description;
  if (body) item.request.body = body;
  if (prerequest.length) item.event.push(event('prerequest', prerequest));
  if (test.length) item.event.push(event('test', test));
  if (!item.event.length) delete item.event;
  return item;
}

const resetDemoScript = [
  'const now = new Date();',
  "const stamp = now.toISOString().replace(/[-:.TZ]/g, '');",
  "const random = Math.random().toString(36).slice(2, 8);",
  "const runId = `${stamp.slice(2)}${random}`;",
  "pm.environment.set('runId', runId);",
  "pm.environment.set('voterEmail', `voter+${runId}@example.com`);",
  "pm.environment.set('districtCode', `WC-${runId.slice(-12)}`);",
  "pm.environment.set('districtName', `Cape Town Demo Ward ${runId.slice(-4)}`);",
  "pm.environment.set('electionName', `YourVoice Verified Election ${runId}`);",
  "pm.environment.set('contestName', `Ward 12 Councillor ${runId}`);",
  "['adminToken', 'adminUserId', 'voterToken', 'voterUserId', 'votingDistrictId', 'electionId', 'contestId', 'optionAId', 'optionBId', 'blankOptionId', 'spoiltOptionId', 'registrationId', 'votingCredential', 'votingCredentialSignature', 'ballotKeyModulus', 'ballotKeyExponent', 'ballotTokenDomain', 'blindingFactor', 'blindedToken', 'secondBlindedToken', 'registrationStartAt', 'registrationEndAt', 'votingStartAt', 'votingEndAt'].forEach((key) => pm.environment.unset(key));",
  '',
  'function southAfricanCheckDigit(first12) {',
  '  let oddPositionSum = 0;',
  '  for (let index = 0; index < 12; index += 2) {',
  '    oddPositionSum += Number(first12[index]);',
  '  }',
  "  let evenPositionDigits = '';",
  '  for (let index = 1; index < 12; index += 2) {',
  '    evenPositionDigits += first12[index];',
  '  }',
  '  const doubledEvenDigitSum = String(Number(evenPositionDigits) * 2)',
  "    .split('')",
  '    .reduce((sum, digit) => sum + Number(digit), 0);',
  '  return (10 - ((oddPositionSum + doubledEvenDigitSum) % 10)) % 10;',
  '}',
  '',
  "const sequence = String(Math.floor(Math.random() * 10000)).padStart(4, '0');",
  'const first12 = `900101${sequence}08`;',
  "pm.environment.set('voterSouthAfricanIdNumber', `${first12}${southAfricanCheckDigit(first12)}`);"
];

const resetDemoTests = [
  ...expectStatus(200, 'API health endpoint is reachable'),
  "pm.test('Verified demo variables were generated', function () {",
  "  pm.expect(pm.environment.get('runId')).to.not.be.empty;",
  "  pm.expect(pm.environment.get('voterEmail')).to.include('@example.com');",
  "  pm.expect(pm.environment.get('voterSouthAfricanIdNumber')).to.match(/^\\d{13}$/);",
  '});'
];

function freshDistrictScript() {
  return [
    ...requireVars(['adminToken']),
    'const now = new Date();',
    "const stamp = now.toISOString().replace(/[-:.TZ]/g, '');",
    "const suffix = `${stamp.slice(8)}${Math.random().toString(36).slice(2, 6)}`;",
    "pm.environment.set('districtCode', `WC-${suffix.slice(-12)}`);",
    "pm.environment.set('districtName', `Cape Town Demo Ward ${suffix.slice(-4)}`);",
    "['votingDistrictId', 'electionId', 'contestId', 'optionAId', 'optionBId', 'blankOptionId', 'spoiltOptionId', 'registrationId', 'votingCredential', 'votingCredentialSignature', 'blindingFactor', 'blindedToken', 'secondBlindedToken'].forEach((key) => pm.environment.unset(key));"
  ];
}

function freshElectionScript() {
  return [
    ...requireVars(['adminToken']),
    'const now = Date.now();',
    "const stamp = new Date().toISOString().replace(/[-:.TZ]/g, '');",
    "const suffix = `${stamp.slice(8)}${Math.random().toString(36).slice(2, 6)}`;",
    "pm.environment.set('electionName', `YourVoice Verified Election ${suffix}`);",
    "pm.environment.set('contestName', `Ward 12 Councillor ${suffix}`);",
    "['electionId', 'contestId', 'optionAId', 'optionBId', 'blankOptionId', 'spoiltOptionId', 'registrationId', 'votingCredential', 'votingCredentialSignature', 'blindingFactor', 'blindedToken', 'secondBlindedToken'].forEach((key) => pm.environment.unset(key));",
    "const configurationGraceSeconds = Math.max(30, Number(pm.environment.get('configurationGraceSeconds') || 120));",
    "const registrationGraceSeconds = Math.max(30, Number(pm.environment.get('registrationGraceSeconds') || 180));",
    "const votingDurationSeconds = Math.max(30, Number(pm.environment.get('votingDurationSeconds') || 90));",
    'const registrationStartAt = new Date(now + configurationGraceSeconds * 1000);',
    'const registrationEndAt = new Date(registrationStartAt.getTime() + registrationGraceSeconds * 1000);',
    'const votingStartAt = new Date(registrationEndAt.getTime() + 5000);',
    'const votingEndAt = new Date(votingStartAt.getTime() + votingDurationSeconds * 1000);',
    "pm.environment.set('registrationStartAt', registrationStartAt.toISOString());",
    "pm.environment.set('registrationEndAt', registrationEndAt.toISOString());",
    "pm.environment.set('votingStartAt', votingStartAt.toISOString());",
    "pm.environment.set('votingEndAt', votingEndAt.toISOString());"
  ];
}

function registrationStillOpenGuard() {
  return [
    ...requireVars(['electionId', 'votingDistrictId', 'voterSouthAfricanIdNumber', 'voterToken', 'registrationStartAt', 'registrationEndAt']),
    "const registrationStartAt = Date.parse(pm.environment.get('registrationStartAt'));",
    "const registrationEndAt = Date.parse(pm.environment.get('registrationEndAt'));",
    'const secondsUntilStart = Math.ceil((registrationStartAt - Date.now()) / 1000);',
    'const secondsRemaining = Math.ceil((registrationEndAt - Date.now()) / 1000);',
    'if (secondsUntilStart > 0) {',
    "  throw new Error(`Registration has not opened yet. Wait ${secondsUntilStart} second(s), then rerun this request.`);",
    '}',
    'if (secondsRemaining <= 0) {',
    "  throw new Error('Registration window expired for this demo election. Run Reset Verified Demo Data and recreate the election, or increase registrationGraceSeconds before Create Election.');",
    '}'
  ];
}

function votingOpenGuard() {
  return [
    ...requireVars(['votingStartAt']),
    "const votingStartAt = Date.parse(pm.environment.get('votingStartAt'));",
    'const secondsRemaining = Math.ceil((votingStartAt - Date.now()) / 1000);',
    'if (secondsRemaining > 0) {',
    "  throw new Error(`Voting window is not open yet. Wait ${secondsRemaining} second(s), then rerun this request.`);",
    '}'
  ];
}

function votingClosedGuard() {
  return [
    ...requireVars(['votingEndAt']),
    "const votingEndAt = Date.parse(pm.environment.get('votingEndAt'));",
    'const secondsRemaining = Math.ceil((votingEndAt - Date.now()) / 1000);',
    'if (secondsRemaining > 0) {',
    "  throw new Error(`Voting window is not closed yet. Wait ${secondsRemaining} second(s), then rerun this request.`);",
    '}'
  ];
}

function electionStatusTests(expectedStatus, label) {
  return [
    ...expectStatus(200, label),
    'const json = pm.response.json();',
    `pm.test('Status is ${expectedStatus}', function () {`,
    `  pm.expect(json.status).to.equal('${expectedStatus}');`,
    '});',
    `if (json.status !== '${expectedStatus}') { throw new Error('The scheduler has not reached ${expectedStatus} yet. Wait a few seconds and rerun this request.'); }`
  ];
}

function contestStatusTests(expectedStatus, label) {
  return [
    ...expectStatus(200, label),
    'const contests = pm.response.json();',
    "const contest = contests.find((item) => item.id === pm.environment.get('contestId'));",
    "if (!contest) { throw new Error('Configured contest was not returned by the API.'); }",
    `pm.test('Contest status is ${expectedStatus}', function () { pm.expect(contest.status).to.equal('${expectedStatus}'); });`,
    `if (contest.status !== '${expectedStatus}') { throw new Error('The scheduler has not moved the contest to ${expectedStatus} yet. Wait a few seconds and rerun this request.'); }`
  ];
}

const collection = {
  info: {
    _postman_id: '76d91d2d-6f35-4fd5-84f1-2bf843d097d8',
    name: 'YourVoice API - Verified Local Flow',
    description: 'Clean verified collection for local Postman testing of the YourVoice secure voting API. Import with the YourVoice Verified Local environment and run folders in order.',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'
  },
  item: [
    {
      name: '00 - Start Here',
      item: [
        request({
          name: 'Reset Verified Demo Data',
          method: 'GET',
          url: '{{baseUrl}}/actuator/health',
          prerequest: resetDemoScript,
          test: resetDemoTests,
          description: 'Run this first. It clears stale demo IDs and generates a unique voter, election, district, and valid South African ID number.'
        }),
        request({ name: 'Health', method: 'GET', url: '{{baseUrl}}/actuator/health', test: expectStatus(200, 'API health is UP') }),
        request({
          name: 'OpenAPI JSON',
          method: 'GET',
          url: '{{baseUrl}}/api-docs',
          test: [
            ...expectStatus(200, 'OpenAPI JSON is available'),
            "pm.test('OpenAPI title is YourVoice API', function () { pm.expect(pm.response.json().info.title).to.equal('YourVoice API'); });"
          ]
        })
      ]
    },
    {
      name: '01 - Admin Auth',
      item: [
        request({
          name: 'Bootstrap First Admin - Optional',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/admin/bootstrap',
          header: headers(['Content-Type', 'application/json'], ['X-YourVoice-Bootstrap-Token', '{{adminBootstrapToken}}']),
          body: jsonBody({ email: '{{adminEmail}}', password: '{{adminPassword}}' }),
          prerequest: requireVars(['adminBootstrapToken', 'adminEmail', 'adminPassword']),
          test: [
            ...expectOneOf([201, 409], 'Admin bootstrap created the first admin or was already used'),
            'if (pm.response.code === 201) {',
            ...storeToken('admin', 'Admin bootstrap').map((line) => `  ${line}`),
            '} else {',
            "  console.warn('Bootstrap already used. Continue with Login Admin.');",
            '}'
          ],
          description: 'Run on a fresh database. If it returns 409, continue with Login Admin.'
        }),
        request({
          name: 'Login Admin',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/auth/login',
          header: jsonHeaders,
          body: jsonBody({ email: '{{adminEmail}}', password: '{{adminPassword}}' }),
          prerequest: requireVars(['adminEmail', 'adminPassword']),
          test: [
            ...expectStatus(200, 'Admin login succeeds'),
            ...storeToken('admin', 'Admin login'),
            "pm.test('Admin role returned', function () { pm.expect(pm.response.json().role).to.equal('ADMIN'); });"
          ]
        }),
        request({
          name: 'Auth Me - Admin',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/auth/me',
          header: adminAuthHeader,
          prerequest: requireVars(['adminToken']),
          test: [
            ...expectStatus(200, 'Authenticated admin profile is returned'),
            "pm.test('Profile role is ADMIN', function () { pm.expect(pm.response.json().role).to.equal('ADMIN'); });"
          ]
        })
      ]
    },
    {
      name: '02 - Election Setup',
      item: [
        request({
          name: 'Create Voting District',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/admin/voting-districts',
          header: adminJsonHeaders,
          body: rawBody('{\n  "code": "{{districtCode}}",\n  "name": "{{districtName}}",\n  "province": "{{province}}",\n  "municipality": "{{municipality}}",\n  "wardNumber": {{wardNumber}}\n}'),
          prerequest: freshDistrictScript(),
          test: [...expectStatus(201, 'Voting district is created'), ...storeId('votingDistrictId', 'Create Voting District')]
        }),
        request({
          name: 'Create Election',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/admin/elections',
          header: adminJsonHeaders,
          body: jsonBody({
            name: '{{electionName}}',
            type: 'MUNICIPAL',
            registrationStartAt: '{{registrationStartAt}}',
            registrationEndAt: '{{registrationEndAt}}',
            votingStartAt: '{{votingStartAt}}',
            votingEndAt: '{{votingEndAt}}'
          }),
          prerequest: freshElectionScript(),
          test: [...expectStatus(201, 'Election is created'), ...storeId('electionId', 'Create Election')]
        }),
        request({
          name: 'Create Municipal Ward Contest',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/admin/elections/{{electionId}}/contests',
          header: adminJsonHeaders,
          body: rawBody('{\n  "name": "{{contestName}}",\n  "type": "MUNICIPAL_WARD",\n  "displayOrder": 1,\n  "scopeProvince": "{{province}}",\n  "scopeMunicipality": "{{municipality}}",\n  "scopeWardNumber": {{wardNumber}}\n}'),
          prerequest: requireVars(['adminToken', 'electionId', 'contestName', 'province', 'municipality', 'wardNumber']),
          test: [...expectStatus(201, 'Contest is created'), ...storeId('contestId', 'Create Contest')]
        }),
        request({
          name: 'Create Option A',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/admin/elections/{{electionId}}/contests/{{contestId}}/options',
          header: adminJsonHeaders,
          body: jsonBody({ name: 'Ubuntu Civic Party', optionType: 'PARTY', displayOrder: 1 }),
          prerequest: requireVars(['adminToken', 'electionId', 'contestId']),
          test: [...expectStatus(201, 'Option A is created'), ...storeId('optionAId', 'Create Option A')]
        }),
        request({
          name: 'Create Option B',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/admin/elections/{{electionId}}/contests/{{contestId}}/options',
          header: adminJsonHeaders,
          body: jsonBody({ name: 'Future Youth Movement', optionType: 'PARTY', displayOrder: 2 }),
          prerequest: requireVars(['adminToken', 'electionId', 'contestId']),
          test: [...expectStatus(201, 'Option B is created'), ...storeId('optionBId', 'Create Option B')]
        }),
        request({
          name: 'Create Blank Ballot Option',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/admin/elections/{{electionId}}/contests/{{contestId}}/options',
          header: adminJsonHeaders,
          body: jsonBody({ name: 'Blank ballot', optionType: 'BLANK_BALLOT', displayOrder: 98 }),
          prerequest: requireVars(['adminToken', 'electionId', 'contestId']),
          test: [...expectStatus(201, 'Blank ballot option is created'), ...storeId('blankOptionId', 'Create Blank Ballot Option')]
        }),
        request({
          name: 'Create Spoilt Ballot Option',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/admin/elections/{{electionId}}/contests/{{contestId}}/options',
          header: adminJsonHeaders,
          body: jsonBody({ name: 'Spoilt ballot', optionType: 'SPOILT_BALLOT', displayOrder: 99 }),
          prerequest: requireVars(['adminToken', 'electionId', 'contestId']),
          test: [...expectStatus(201, 'Spoilt ballot option is created'), ...storeId('spoiltOptionId', 'Create Spoilt Ballot Option')]
        }),
        request({
          name: 'Timing Check - Registration Window Open',
          method: 'GET',
          url: '{{baseUrl}}/actuator/health',
          prerequest: registrationStillOpenGuard(),
          test: expectStatus(200, 'Registration start time has arrived')
        }),
        request({
          name: 'Confirm Election Is REGISTRATION_OPEN',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}',
          prerequest: requireVars(['electionId']),
          test: electionStatusTests('REGISTRATION_OPEN', 'Election status is available')
        })
      ]
    },
    {
      name: '03 - Public Reads',
      item: [
        request({ name: 'List Voting Districts', method: 'GET', url: '{{baseUrl}}/api/v1/voting-districts', test: expectStatus(200, 'Voting districts are public') }),
        request({ name: 'List Elections', method: 'GET', url: '{{baseUrl}}/api/v1/elections', test: expectStatus(200, 'Elections are public') }),
        request({ name: 'Get Election', method: 'GET', url: '{{baseUrl}}/api/v1/elections/{{electionId}}', prerequest: requireVars(['electionId']), test: expectStatus(200, 'Election details are public') }),
        request({ name: 'List Contests', method: 'GET', url: '{{baseUrl}}/api/v1/elections/{{electionId}}/contests', prerequest: requireVars(['electionId']), test: expectStatus(200, 'Contest list is public') })
      ]
    },
    {
      name: '04 - Voter Registration',
      item: [
        request({
          name: 'Register Voter Account',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/auth/register',
          header: jsonHeaders,
          body: jsonBody({
            fullName: '{{voterFullName}}',
            email: '{{voterEmail}}',
            password: '{{voterPassword}}',
            southAfricanIdNumber: '{{voterSouthAfricanIdNumber}}',
            idDocumentType: '{{idDocumentType}}',
            votingDistrictId: '{{votingDistrictId}}'
          }),
          prerequest: requireVars([
            'voterFullName',
            'voterEmail',
            'voterPassword',
            'voterSouthAfricanIdNumber',
            'idDocumentType',
            'votingDistrictId'
          ]),
          test: [
            ...expectOneOf([201, 409], 'Voter account is created or already exists'),
            'if (pm.response.code === 201) {',
            ...storeToken('voter', 'Register Voter Account').map((line) => `  ${line}`),
            '} else {',
            "  console.warn('Voter account already exists. Continue with Login Voter.');",
            '}'
          ]
        }),
        request({
          name: 'Login Voter',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/auth/login',
          header: jsonHeaders,
          body: jsonBody({ email: '{{voterEmail}}', password: '{{voterPassword}}' }),
          prerequest: requireVars(['voterEmail', 'voterPassword']),
          test: [
            ...expectStatus(200, 'Voter login succeeds'),
            ...storeToken('voter', 'Voter login'),
            "pm.test('Voter role returned', function () { pm.expect(pm.response.json().role).to.equal('VOTER'); });"
          ]
        }),
        request({
          name: 'Auth Me - Voter',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/auth/me',
          header: voterAuthHeader,
          prerequest: requireVars(['voterToken']),
          test: [
            ...expectStatus(200, 'Authenticated voter profile is returned'),
            "pm.test('Profile role is VOTER', function () { pm.expect(pm.response.json().role).to.equal('VOTER'); });"
          ]
        }),
        request({
          name: 'Register Voter For Election',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}/registrations',
          header: voterJsonHeaders,
          body: jsonBody({ votingDistrictId: '{{votingDistrictId}}' }),
          prerequest: registrationStillOpenGuard(),
          test: [
            ...expectOneOf([201, 409], 'Election registration is created or already exists'),
            'if (pm.response.code === 201) {',
            ...storeId('registrationId', 'Register Voter For Election').map((line) => `  ${line}`),
            '} else {',
            '  const message = pm.response.json().message || pm.response.text();',
            "  if (!message.includes('already registered')) { throw new Error(`Election registration failed (${pm.response.code}): ${pm.response.text()}`); }",
            '}'
          ]
        }),
        request({
          name: 'My Registrations',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/me/registrations',
          header: voterAuthHeader,
          prerequest: requireVars(['voterToken']),
          test: [
            ...expectStatus(200, 'Voter registrations are returned'),
            "pm.test('At least one registration is present', function () { pm.expect(pm.response.json().length).to.be.greaterThan(0); });"
          ]
        })
      ]
    },
    {
      name: '05 - Voting',
      item: [
        request({ name: 'Timing Check - Voting Window Open', method: 'GET', url: '{{baseUrl}}/actuator/health', prerequest: votingOpenGuard(), test: expectStatus(200, 'Voting window is open') }),
        request({
          name: 'Confirm Election Is VOTING_OPEN',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}',
          prerequest: requireVars(['electionId']),
          test: electionStatusTests('VOTING_OPEN', 'Election status is available')
        }),
        request({
          name: 'Confirm Contest Is OPEN',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}/contests',
          prerequest: requireVars(['electionId', 'contestId']),
          test: contestStatusTests('OPEN', 'Contest list is available')
        }),
        request({
          name: 'Get Contest Ballot Key',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}/contests/{{contestId}}/ballot-key',
          prerequest: requireVars(['electionId', 'contestId']),
          test: [
            ...expectStatus(200, 'Ballot key is published'),
            'const json = pm.response.json();',
            "pm.environment.set('ballotKeyModulus', json.modulus);",
            "pm.environment.set('ballotKeyExponent', json.publicExponent);",
            "pm.environment.set('ballotTokenDomain', json.tokenDomain);"
          ]
        }),
        request({
          name: 'Issue Voting Credential',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}/contests/{{contestId}}/credentials',
          header: voterJsonHeaders,
          body: jsonBody({ blindedToken: '{{blindedToken}}' }),
          prerequest: [
            ...requireVars(['voterToken', 'electionId', 'contestId', 'ballotKeyModulus', 'ballotKeyExponent', 'ballotTokenDomain']),
            ...votingOpenGuard(),
            ...blindSignatureHelpers,
            "const modulus = hexToBig(b64uToHex(pm.environment.get('ballotKeyModulus')));",
            "const exponent = hexToBig(b64uToHex(pm.environment.get('ballotKeyExponent')));",
            "const ballotToken = hexToB64u(randomHex(32));",
            'let blindingFactor = 0n;',
            "while (blindingFactor <= 1n || gcd(blindingFactor, modulus) !== 1n) { blindingFactor = hexToBig(randomHex(byteLength(modulus))) % modulus; }",
            "const hash = fullDomainHash(pm.environment.get('ballotTokenDomain'), pm.environment.get('contestId'), ballotToken, modulus);",
            'const blinded = (hash * modPow(blindingFactor, exponent, modulus)) % modulus;',
            "pm.environment.set('votingCredential', ballotToken);",
            "pm.environment.set('blindingFactor', bigToHex(blindingFactor));",
            "pm.environment.set('blindedToken', hexToB64u(bigToHex(blinded)));"
          ],
          test: [
            ...expectStatus(201, 'Voting credential is issued'),
            ...blindSignatureHelpers,
            'const json = pm.response.json();',
            "if (!json.blindSignature) { throw new Error('Issue Voting Credential response did not include blindSignature.'); }",
            "const modulus = hexToBig(b64uToHex(pm.environment.get('ballotKeyModulus')));",
            "const exponent = hexToBig(b64uToHex(pm.environment.get('ballotKeyExponent')));",
            "const signature = (hexToBig(b64uToHex(json.blindSignature)) * modInverse(hexToBig(pm.environment.get('blindingFactor')), modulus)) % modulus;",
            "const expected = fullDomainHash(pm.environment.get('ballotTokenDomain'), pm.environment.get('contestId'), pm.environment.get('votingCredential'), modulus);",
            "pm.test('Unblinded signature verifies against the contest key', function () { pm.expect(modPow(signature, exponent, modulus) === expected).to.equal(true); });",
            "pm.environment.set('votingCredentialSignature', hexToB64u(bigToHex(signature)));"
          ]
        }),
        request({
          name: 'Cast Ballot - Option A',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/ballots',
          header: jsonHeaders,
          body: jsonBody({ contestId: '{{contestId}}', contestOptionId: '{{optionAId}}', ballotToken: '{{votingCredential}}', signature: '{{votingCredentialSignature}}' }),
          prerequest: [...requireVars(['contestId', 'optionAId', 'votingCredential', 'votingCredentialSignature']), ...votingOpenGuard()],
          test: [
            ...expectStatus(201, 'Ballot is accepted'),
            "pm.test('Ballot response is accepted', function () { pm.expect(pm.response.json().accepted).to.equal(true); });"
          ]
        })
      ]
    },
    {
      name: '06 - Results and Audit',
      item: [
        request({ name: 'Timing Check - Voting Window Closed', method: 'GET', url: '{{baseUrl}}/actuator/health', prerequest: votingClosedGuard(), test: expectStatus(200, 'Voting window is closed') }),
        request({
          name: 'Confirm Election Is COMPLETED',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}',
          prerequest: requireVars(['electionId']),
          test: electionStatusTests('COMPLETED', 'Election status is available')
        }),
        request({
          name: 'Confirm Contest Is CLOSED',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}/contests',
          prerequest: requireVars(['electionId', 'contestId']),
          test: contestStatusTests('CLOSED', 'Contest list is available')
        }),
        request({
          name: 'Get Final Results',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}/contests/{{contestId}}/results',
          prerequest: [...requireVars(['electionId', 'contestId']), ...votingClosedGuard()],
          test: [
            ...expectStatus(200, 'Final results are available'),
            "pm.test('At least one ballot and valid vote counted', function () { const json = pm.response.json(); pm.expect(json.ballotsCast).to.be.at.least(1); pm.expect(json.validVotes).to.be.at.least(1); });"
          ]
        }),
        request({
          name: 'Verify Hash Chain',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}/contests/{{contestId}}/audit',
          prerequest: requireVars(['electionId', 'contestId']),
          test: [
            ...expectStatus(200, 'Hash chain audit is available'),
            "pm.test('Hash chain is valid', function () { const json = pm.response.json(); pm.expect(json.chainValid).to.equal(true); pm.expect(json.ledgerEntryCount).to.be.at.least(1); });"
          ]
        }),
        request({
          name: 'List Public Ledger',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}/contests/{{contestId}}/ledger',
          prerequest: requireVars(['electionId', 'contestId']),
          test: [
            ...expectStatus(200, 'Public ledger is available'),
            "pm.test('Ledger contains anonymized entry', function () { const json = pm.response.json(); pm.expect(json.length).to.be.at.least(1); pm.expect(json[0]).to.have.property('currentHash'); });"
          ]
        }),
        request({
          name: 'Admin Security Audit Events',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/admin/security-audit-events?limit=25',
          header: adminAuthHeader,
          prerequest: requireVars(['adminToken']),
          test: expectStatus(200, 'Admin security audit events are returned')
        }),
        request({
          name: 'Admin Election Lifecycle Events',
          method: 'GET',
          url: '{{baseUrl}}/api/v1/admin/elections/{{electionId}}/lifecycle-events',
          header: adminAuthHeader,
          prerequest: requireVars(['adminToken', 'electionId']),
          test: [
            ...expectStatus(200, 'Election lifecycle audit events are returned'),
            "pm.test('Automatic lifecycle is fully audited', function () { const events = pm.response.json(); pm.expect(events.length).to.be.at.least(4); pm.expect(events.every((item) => item.trigger === 'AUTOMATIC')).to.equal(true); });"
          ]
        })
      ]
    },
    {
      name: '07 - Negative Checks',
      item: [
        request({
          name: 'Invalid Login Returns 401',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/auth/login',
          header: jsonHeaders,
          body: jsonBody({ email: '{{voterEmail}}', password: 'WrongPassword1' }),
          prerequest: requireVars(['voterEmail']),
          test: expectStatus(401, 'Invalid login is rejected')
        }),
        request({
          name: 'Duplicate Credential Returns 409',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/elections/{{electionId}}/contests/{{contestId}}/credentials',
          header: voterJsonHeaders,
          body: jsonBody({ blindedToken: '{{secondBlindedToken}}' }),
          prerequest: [
            ...requireVars(['voterToken', 'electionId', 'contestId', 'ballotKeyModulus']),
            ...blindSignatureHelpers,
            "const modulus = hexToBig(b64uToHex(pm.environment.get('ballotKeyModulus')));",
            "pm.environment.set('secondBlindedToken', hexToB64u(bigToHex((hexToBig(randomHex(byteLength(modulus))) % (modulus - 2n)) + 2n)));"
          ],
          test: expectStatus(409, 'A second credential for a different blinded token is rejected')
        }),
        request({
          name: 'Reuse Voting Credential Returns 409',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/ballots',
          header: jsonHeaders,
          body: jsonBody({ contestId: '{{contestId}}', contestOptionId: '{{optionBId}}', ballotToken: '{{votingCredential}}', signature: '{{votingCredentialSignature}}' }),
          prerequest: requireVars(['contestId', 'optionBId', 'votingCredential', 'votingCredentialSignature']),
          test: expectStatus(409, 'Reused voting credential is rejected')
        }),
        request({
          name: 'Invalid Voting Credential Returns 401',
          method: 'POST',
          url: '{{baseUrl}}/api/v1/ballots',
          header: jsonHeaders,
          body: jsonBody({ contestId: '{{contestId}}', contestOptionId: '{{optionAId}}', ballotToken: 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', signature: 'AQID' }),
          prerequest: requireVars(['contestId', 'optionAId']),
          test: expectStatus(401, 'Invalid voting credential is rejected')
        })
      ]
    }
  ]
};

const environment = {
  id: 'f5b45db1-a5ce-4f17-b03e-19f5590902b9',
  name: 'YourVoice Verified Local',
  values: [
    { key: 'baseUrl', value: 'http://localhost:8080', type: 'default', enabled: true },
    { key: 'adminBootstrapToken', value: 'change-me-local-admin-bootstrap-token-at-least-32-characters', type: 'secret', enabled: true },
    { key: 'adminEmail', value: 'admin@yourvoice.local', type: 'default', enabled: true },
    { key: 'adminPassword', value: 'VeryStrongPassword1', type: 'secret', enabled: true },
    { key: 'voterPassword', value: 'VeryStrongPassword1', type: 'secret', enabled: true },
    { key: 'configurationGraceSeconds', value: '120', type: 'default', enabled: true },
    { key: 'registrationGraceSeconds', value: '180', type: 'default', enabled: true },
    { key: 'votingDurationSeconds', value: '90', type: 'default', enabled: true },
    { key: 'province', value: 'Western Cape', type: 'default', enabled: true },
    { key: 'municipality', value: 'City of Cape Town', type: 'default', enabled: true },
    { key: 'wardNumber', value: '12', type: 'default', enabled: true },
    { key: 'idDocumentType', value: 'SMART_ID_CARD', type: 'default', enabled: true },
    { key: 'runId', value: '', type: 'default', enabled: true },
    { key: 'voterFullName', value: 'Verified Postman Voter', type: 'default', enabled: true },
    { key: 'voterEmail', value: '', type: 'default', enabled: true },
    { key: 'voterSouthAfricanIdNumber', value: '', type: 'default', enabled: true },
    { key: 'districtCode', value: '', type: 'default', enabled: true },
    { key: 'districtName', value: '', type: 'default', enabled: true },
    { key: 'electionName', value: '', type: 'default', enabled: true },
    { key: 'contestName', value: '', type: 'default', enabled: true },
    { key: 'registrationStartAt', value: '', type: 'default', enabled: true },
    { key: 'registrationEndAt', value: '', type: 'default', enabled: true },
    { key: 'votingStartAt', value: '', type: 'default', enabled: true },
    { key: 'votingEndAt', value: '', type: 'default', enabled: true },
    { key: 'adminToken', value: '', type: 'secret', enabled: true },
    { key: 'adminUserId', value: '', type: 'default', enabled: true },
    { key: 'voterToken', value: '', type: 'secret', enabled: true },
    { key: 'voterUserId', value: '', type: 'default', enabled: true },
    { key: 'votingDistrictId', value: '', type: 'default', enabled: true },
    { key: 'electionId', value: '', type: 'default', enabled: true },
    { key: 'contestId', value: '', type: 'default', enabled: true },
    { key: 'optionAId', value: '', type: 'default', enabled: true },
    { key: 'optionBId', value: '', type: 'default', enabled: true },
    { key: 'blankOptionId', value: '', type: 'default', enabled: true },
    { key: 'spoiltOptionId', value: '', type: 'default', enabled: true },
    { key: 'registrationId', value: '', type: 'default', enabled: true },
    { key: 'votingCredential', value: '', type: 'secret', enabled: true },
    { key: 'votingCredentialSignature', value: '', type: 'secret', enabled: true },
    { key: 'ballotKeyModulus', value: '', type: 'default', enabled: true },
    { key: 'ballotKeyExponent', value: '', type: 'default', enabled: true },
    { key: 'ballotTokenDomain', value: '', type: 'default', enabled: true },
    { key: 'blindingFactor', value: '', type: 'secret', enabled: true },
    { key: 'blindedToken', value: '', type: 'default', enabled: true },
    { key: 'secondBlindedToken', value: '', type: 'default', enabled: true }
  ],
  _postman_variable_scope: 'environment',
  _postman_exported_using: 'Codex'
};

fs.writeFileSync(collectionPath, JSON.stringify(collection, null, 2) + '\n');
fs.writeFileSync(environmentPath, JSON.stringify(environment, null, 2) + '\n');
const compatibilityCollection = structuredClone(collection);
compatibilityCollection.info.name = 'YourVoice API';
const compatibilityEnvironment = structuredClone(environment);
compatibilityEnvironment.name = 'YourVoice Local';
fs.writeFileSync(compatibilityCollectionPath, JSON.stringify(compatibilityCollection, null, 2) + '\n');
fs.writeFileSync(compatibilityEnvironmentPath, JSON.stringify(compatibilityEnvironment, null, 2) + '\n');
console.log(`Wrote ${collectionPath}`);
console.log(`Wrote ${environmentPath}`);
console.log(`Wrote ${compatibilityCollectionPath}`);
console.log(`Wrote ${compatibilityEnvironmentPath}`);
