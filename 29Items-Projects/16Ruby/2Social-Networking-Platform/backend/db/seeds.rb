demo = User.find_or_create_by!(email: "demo@example.com") do |user|
  user.username = "demo_user"
  user.display_name = "Demo User"
  user.password = "password123"
  user.password_confirmation = "password123"
end

friend = User.find_or_create_by!(email: "friend@example.com") do |user|
  user.username = "friend_user"
  user.display_name = "Friend User"
  user.password = "password123"
  user.password_confirmation = "password123"
end

Follow.find_or_create_by!(follower: demo, followee: friend)

Posts::CreatePost.call(user: demo, attributes: { body: "Welcome to the social platform.", visibility: "public" }) unless demo.posts.exists?

unless friend.posts.exists?
  Posts::CreatePost.call(
    user: friend,
    attributes: { body: "Search, feeds, messages, and notifications are live.", visibility: "public" }
  )
end
