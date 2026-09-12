return unless Rails.env.development?

puts "Seeding..."

admin = User.find_or_create_by!(email: "admin@example.com") do |u|
  u.name = "Admin User"
  u.password = "password123"
  u.admin_flag = true
end

alice = User.find_or_create_by!(email: "alice@example.com") do |u|
  u.name = "Alice Dev"
  u.password = "password123"
end

bob = User.find_or_create_by!(email: "bob@example.com") do |u|
  u.name = "Bob Dev"
  u.password = "password123"
end

project = Project.find_or_create_by!(key: "DEMO") do |p|
  p.name = "Demo Project"
  p.description = "A seeded demo project."
end

[admin, alice, bob].each do |u|
  Membership.find_or_create_by!(user: u, project: project) do |m|
    m.role = u == admin ? "admin" : "member"
  end
end

sprint = Sprint.find_or_create_by!(project: project, name: "Sprint 1") do |s|
  s.starts_on = Date.current - 2.days
  s.ends_on = Date.current + 12.days
  s.state = "active"
  s.goal = "Ship the MVP issue board."
end

10.times do |i|
  Issue.find_or_create_by!(project: project, title: "Demo Issue ##{i + 1}") do |issue|
    issue.reporter = admin
    issue.assignee = [alice, bob, nil].sample
    issue.sprint = sprint
    issue.status = Issue::STATUSES.sample
    issue.priority = Issue::PRIORITIES.sample
    issue.estimate_hours = [2, 4, 8, 16].sample
    issue.description = "Auto-seeded issue #{i + 1}."
  end
end

puts "Done. Sign in with admin@example.com / password123"
