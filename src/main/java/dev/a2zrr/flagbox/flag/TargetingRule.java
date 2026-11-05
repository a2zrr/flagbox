package dev.a2zrr.flagbox.flag;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A rule that decides a flag's value for matching users. Rules are stored as a
 * JSON array with a {@code type} discriminator, for example:
 *
 * <pre>
 * {"type": "USER_LIST", "userIds": ["user-1"], "value": true}
 * {"type": "ATTRIBUTE", "attribute": "country", "operator": "IN", "values": ["FR", "DZ"], "value": true}
 * </pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserListRule.class, name = "USER_LIST"),
    @JsonSubTypes.Type(value = AttributeRule.class, name = "ATTRIBUTE")
})
public sealed interface TargetingRule permits UserListRule, AttributeRule {

    /** The value served when this rule matches: {@code true} for on, {@code false} for off. */
    boolean value();
}
