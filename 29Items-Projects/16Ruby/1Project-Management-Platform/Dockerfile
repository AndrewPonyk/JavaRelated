FROM ruby:3.3.0-slim

ENV BUNDLE_PATH=/usr/local/bundle \
    LANG=C.UTF-8

RUN apt-get update -qq && apt-get install -y --no-install-recommends \
    build-essential libpq-dev libyaml-dev nodejs curl git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY Gemfile Gemfile.lock* ./
RUN bundle lock --add-platform x86_64-linux aarch64-linux 2>/dev/null || true \
 && bundle install --jobs=4 --retry=3

COPY . .

EXPOSE 3000

CMD ["bundle", "exec", "puma", "-C", "config/puma.rb"]
