require "rails_helper"

RSpec.describe "domain models" do
  it "prevents duplicate follows and self follows" do
    user = create(:user)
    other = create(:user)

    create(:follow, follower: user, followee: other)

    expect(build(:follow, follower: user, followee: other)).not_to be_valid
    expect(build(:follow, follower: user, followee: user)).not_to be_valid
  end

  it "limits post visibility to supported values" do
    expect(build(:post, visibility: "public")).to be_valid
    expect(build(:post, visibility: "everyone")).not_to be_valid
  end

  it "requires valid user identity fields and password length" do
    expect(build(:user, email: "bad", username: "valid_user")).not_to be_valid
    expect(build(:user, email: "ok@example.com", username: "no spaces")).not_to be_valid
    expect(build(:user, password: "short", password_confirmation: "short")).not_to be_valid
  end

  it "requires toxicity results to target exactly one record" do
    user = create(:user)
    post = create(:post, user: user)
    message = create(:message, sender: user, recipient: create(:user))

    expect(build(:toxicity_result, post: post, message: nil)).to be_valid
    expect(build(:toxicity_result, post: post, message: message)).not_to be_valid
  end

  it "scopes post and message visibility by viewer" do
    author = create(:user)
    follower = create(:user)
    outsider = create(:user)
    create(:follow, follower: follower, followee: author)
    public_post = create(:post, user: author, visibility: "public")
    follower_post = create(:post, user: author, visibility: "followers")
    private_post = create(:post, user: author, visibility: "private")
    message = create(:message, sender: author, recipient: follower)

    expect(Post.visible_to(nil)).to contain_exactly(public_post)
    expect(Post.visible_to(follower)).to include(public_post, follower_post)
    expect(Post.visible_to(outsider)).not_to include(follower_post, private_post)
    expect(Message.visible_to(follower)).to include(message)
    expect(Message.visible_to(outsider)).not_to include(message)
  end
end
