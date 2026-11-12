import { createGitHubClient } from './github.js';
import { ALL_LABELS } from './stages.js';

const PURPLE = '6b5bff';
const GREY = '8a8a8a';
const RED = 'd73a4a';

export function labelColor(name) {
  if (name === 'factory') return GREY;
  if (name === 'factory:blocked') return RED;
  return PURPLE;
}

async function main() {
  const token = process.env.GITHUB_TOKEN;
  if (!token) {
    console.error('GITHUB_TOKEN is missing. Put it in factory/dashboard/.env (see .env.example).');
    process.exit(1);
  }
  const repo = process.env.GITHUB_REPO || 'vladcipariu91/guess-the-country-mobile';
  const github = createGitHubClient({ token, repo });
  for (const name of ALL_LABELS) {
    // createLabel ignores 422 (already exists), so check first to report accurately.
    const res = await fetch(`https://api.github.com/repos/${repo}/labels/${encodeURIComponent(name)}`, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' },
    });
    if (res.ok) {
      console.log(`exists   ${name}`);
      continue;
    }
    await github.createLabel(name, labelColor(name));
    console.log(`created  ${name}`);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error(`Setup failed: ${err.message}`);
    process.exit(1);
  });
}
