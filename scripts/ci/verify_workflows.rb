#!/usr/bin/env ruby
# Structural checks prevent accidental exposure when editing the two workflows.
require 'psych'
root = File.expand_path('../..', __dir__)
allowed_actions = %w[actions/checkout actions/setup-java google-github-actions/auth]
workflows = Dir[File.join(root, '.github/workflows/*.yml')].to_h do |path|
  source = File.read(path)
  data = Psych.safe_load(source, aliases: false)
  raise 'No public artifact/cache uploads are permitted' if source.match?(/upload-artifact|actions\/cache|cache:\s*(gradle|maven)/)
  raise 'Workflow permissions must be read-only by default' unless data.fetch('permissions') == {'contents'=>'read'}
  data.fetch('jobs').each_value do |job|
    raise 'Use the standard runner only' unless job.fetch('runs-on') == 'ubuntu-24.04'
    raise 'Runner context is unavailable in job env' if job.fetch('env', {}).values.any? { |v| v.to_s.include?('runner.') }
    job.fetch('steps').each do |step|
      next unless step['uses']
      action, sha = step['uses'].split('@')
      raise 'Action must be trusted and pinned to a commit' unless allowed_actions.include?(action) && sha&.match?(/\A[0-9a-f]{40}\z/)
    end
  end
  [File.basename(path), [data, source]]
end
checks, check_source = workflows.fetch('checks.yml')
raise 'Checks must run on PRs and main pushes only' unless checks.fetch('on').keys.sort == %w[pull_request push]
checks.fetch('on').each_value { |event| raise 'Checks target main only' unless event.fetch('branches') == ['main'] }
raise 'Automatic checks must not use secrets, OIDC or environments' if check_source.match?(/secrets\.|id-token:|environment:/)
release, release_source = workflows.fetch('internal-play.yml')
raise 'Release must be manual only' unless release.fetch('on').keys == ['workflow_dispatch']
job = release.fetch('jobs').fetch('private_build')
raise 'Release must use protected environment' unless job.fetch('environment') == 'play-internal'
raise 'OIDC must belong only to private job' unless job.fetch('permissions') == {'contents'=>'read','id-token'=>'write'}
raise 'Trusted-main owner check missing' unless release.fetch('jobs').fetch('verify').fetch('if').include?("github.ref == 'refs/heads/main'") && release.fetch('jobs').fetch('verify').fetch('if').include?("github.actor_id == '61886'")
raise 'Private cleanup must always run' unless job.fetch('steps').last.fetch('if') == 'always()' && job.fetch('steps').last.fetch('run').end_with?(' cleanup')
raise 'Private Gradle diagnostics must not reach logs' if job.fetch('steps').any? { |step| step.fetch('run', '').include?('./gradlew') }
raise 'Token persistence prohibited' unless job.fetch('steps').find { |s| s['id']=='auth' }.fetch('with').slice('create_credentials_file','export_environment_variables') == {'create_credentials_file'=>false,'export_environment_variables'=>false}
puts 'Workflow safety verified: secret-free automatic checks, protected manual internal release, pinned actions, private cleanup, no artifacts.'
