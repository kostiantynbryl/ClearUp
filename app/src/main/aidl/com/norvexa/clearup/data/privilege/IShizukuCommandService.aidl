package com.norvexa.clearup.data.privilege;

interface IShizukuCommandService {
    void destroy() = 16777114;
    String[] execute(String operation, String packageName, int userId) = 1;
}
