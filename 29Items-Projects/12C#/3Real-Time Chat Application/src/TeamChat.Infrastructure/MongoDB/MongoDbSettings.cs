namespace TeamChat.Infrastructure.MongoDB;

public class MongoDbSettings
{
    public const string SectionName = "MongoDB";

    public string ConnectionString { get; set; } = "mongodb://localhost:27017";
    public string DatabaseName { get; set; } = "TeamChat";
}
