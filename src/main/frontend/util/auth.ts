import {configureAuth} from '@vaadin/hilla-react-auth';
import {UserEndpoint} from 'Frontend/generated/endpoints';

// Configure auth to use `UserInfoService.getUserInfo`
const auth = configureAuth(UserEndpoint.getUserInfo);

// Export auth provider and useAuth hook, which are automatically
// typed to the result of `UserInfoService.getUserInfo`
export const useAuth = auth.useAuth;
export const AuthProvider = auth.AuthProvider;