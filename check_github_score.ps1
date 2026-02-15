$response = Invoke-WebRequest -Uri 'https://api.github.com/repos/CSM218/ipc-rpc-parallel-computing-emmanuelmgovo-gif/actions/runs' -UseBasicParsing -ErrorAction SilentlyContinue

if ($response) {
    $json = $response.Content | ConvertFrom-Json
    Write-Host "Total runs: $($json.total_count)"
    $json.workflow_runs | Select-Object -First 3 | ForEach-Object {
        Write-Host "`nRun ID: $($_.id)"
        Write-Host "Name: $($_.name)"
        Write-Host "Status: $($_.status)"
        Write-Host "Conclusion: $($_.conclusion)"
        Write-Host "Updated: $($_.updated_at)"
    }
} else {
    Write-Host "Failed to get response"
}
