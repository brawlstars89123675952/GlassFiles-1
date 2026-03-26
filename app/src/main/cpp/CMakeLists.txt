cmake_minimum_required(VERSION 3.22.1)
project("glassfiles-pty")
add_library(glassfiles-pty SHARED pty.c)
target_link_libraries(glassfiles-pty log)

# Security native library
add_library(glassfiles-security SHARED security.c)
target_link_libraries(glassfiles-security log dl)
