package com.linkedin.datahub.graphql.resolvers.role;

import com.datahub.authentication.Authentication;
import com.datahub.authorization.role.RoleService;
import com.linkedin.common.urn.Urn;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.exception.AuthorizationException;
import com.linkedin.datahub.graphql.generated.BatchAssignRoleInput;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.linkedin.datahub.graphql.authorization.AuthorizationUtils.*;
import static com.linkedin.datahub.graphql.resolvers.ResolverUtils.*;


@Slf4j
@RequiredArgsConstructor
public class BatchAssignRoleResolver implements DataFetcher<CompletableFuture<Boolean>> {
  private final RoleService _roleService;

  @Override
  public CompletableFuture<Boolean> get(DataFetchingEnvironment environment) throws Exception {
    final QueryContext context = environment.getContext();
    if (!canManagePolicies(context)) {
      throw new AuthorizationException(
          "Unauthorized to assign roles. Please contact your DataHub administrator if this needs corrective action.");
    }

    final BatchAssignRoleInput input = bindArgument(environment.getArgument("input"), BatchAssignRoleInput.class);
    final String roleUrnStr = input.getRoleUrn();
    final List<String> actors = input.getActors();
    final Authentication authentication = context.getAuthentication();

    return CompletableFuture.supplyAsync(() -> {
      try {
        Urn roleUrn = Urn.createFromString(roleUrnStr);
        if (!_roleService.exists(roleUrn, authentication)) {
          throw new RuntimeException(String.format("Role %s does not exist", roleUrnStr));
        }

        actors.forEach(actor -> {
          try {
            _roleService.assignRoleToActor(actor, roleUrn, authentication);
          } catch (Exception e) {
            log.warn(
                String.format("Failed to assign role %s to actor %s. Skipping actor assignment", roleUrnStr, actor), e);
          }
        });
        return true;
      } catch (Exception e) {
        throw new RuntimeException(String.format("Failed to perform update against input %s", input), e);
      }
    });
  }
}
